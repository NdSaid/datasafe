package de.adorsys.datasafe.business.impl.e2e;

import com.google.common.io.ByteStreams;
import de.adorsys.datasafe.business.api.inbox.actions.ListInbox;
import de.adorsys.datasafe.business.api.inbox.actions.ReadFromInbox;
import de.adorsys.datasafe.business.api.inbox.actions.RemoveFromInbox;
import de.adorsys.datasafe.business.api.inbox.actions.WriteToInbox;
import de.adorsys.datasafe.business.api.profile.operations.ProfileRegistrationService;
import de.adorsys.datasafe.business.api.profile.operations.ProfileRemovalService;
import de.adorsys.datasafe.business.api.types.CreateUserPrivateProfile;
import de.adorsys.datasafe.business.api.types.CreateUserPublicProfile;
import de.adorsys.datasafe.business.api.types.UserID;
import de.adorsys.datasafe.business.api.types.UserIDAuth;
import de.adorsys.datasafe.business.api.types.action.ListRequest;
import de.adorsys.datasafe.business.api.types.action.ReadRequest;
import de.adorsys.datasafe.business.api.types.action.RemoveRequest;
import de.adorsys.datasafe.business.api.types.action.WriteRequest;
import de.adorsys.datasafe.business.api.types.keystore.ReadKeyPassword;
import de.adorsys.datasafe.business.api.types.resource.*;
import de.adorsys.datasafe.business.impl.privatespace.actions.ListPrivate;
import de.adorsys.datasafe.business.impl.privatespace.actions.ReadFromPrivate;
import de.adorsys.datasafe.business.impl.privatespace.actions.RemoveFromPrivate;
import de.adorsys.datasafe.business.impl.privatespace.actions.WriteToPrivate;
import de.adorsys.datasafe.business.impl.service.DefaultDatasafeServices;
import de.adorsys.datasafe.business.api.types.utils.Log;
import de.adorsys.datasafe.business.impl.service.VersionedDatasafeServices;
import de.adorsys.datasafe.shared.BaseMockitoTest;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.Lists;

import java.io.*;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public abstract class BaseE2ETest extends BaseMockitoTest {

    protected static final String PRIVATE_COMPONENT = "private";
    protected static final String PRIVATE_FILES_COMPONENT = PRIVATE_COMPONENT + "/files";
    protected static final String INBOX_COMPONENT = "inbox";
    protected static final String VERSION_COMPONENT = "versions";
    protected static final List<String> loadReport = Lists.newArrayList();

    protected ListPrivate listPrivate;
    protected ReadFromPrivate readFromPrivate;
    protected WriteToPrivate writeToPrivate;
    protected RemoveFromPrivate removeFromPrivate;
    protected ReadFromInbox readFromInbox;
    protected ListInbox listInbox;
    protected WriteToInbox writeToInbox;
    protected RemoveFromInbox removeFromInbox;
    protected ProfileRegistrationService profileRegistrationService;
    protected ProfileRemovalService profileRemovalService;

    protected UserIDAuth john;
    protected UserIDAuth jane;

    protected void initialize(DefaultDatasafeServices datasafeServices) {
        this.listPrivate = datasafeServices.privateService();
        this.readFromPrivate = datasafeServices.privateService();
        this.writeToPrivate = datasafeServices.privateService();
        this.removeFromPrivate = datasafeServices.privateService();
        this.readFromInbox = datasafeServices.inboxService();
        this.listInbox = datasafeServices.inboxService();
        this.writeToInbox = datasafeServices.inboxService();
        this.removeFromInbox = datasafeServices.inboxService();
        this.profileRegistrationService = datasafeServices.userProfile();
        this.profileRemovalService = datasafeServices.userProfile();
    }

    protected void initialize(VersionedDatasafeServices datasafeServices) {
        this.listPrivate = datasafeServices.privateService();
        this.readFromPrivate = datasafeServices.privateService();
        this.writeToPrivate = datasafeServices.privateService();
        this.removeFromPrivate = datasafeServices.privateService();
        this.readFromInbox = datasafeServices.inboxService();
        this.listInbox = datasafeServices.inboxService();
        this.writeToInbox = datasafeServices.inboxService();
        this.removeFromInbox = datasafeServices.inboxService();
        this.profileRegistrationService = datasafeServices.userProfile();
        this.profileRemovalService = datasafeServices.userProfile();
    }

    @SneakyThrows
    protected void writeDataToPrivate(UserIDAuth auth, String path, String data) {
        OutputStream stream = writeToPrivate.write(WriteRequest.forDefaultPrivate(auth, path));
        stream.write(data.getBytes());
        stream.close();
        log.info("File {} of user {} saved to {}", Log.secure(data), Log.secure(auth),
                Log.secure(path.split("/"), "/"));
    }

    protected AbsoluteResourceLocation<PrivateResource> getFirstFileInPrivate(UserIDAuth owner) {
        return getAllFilesInPrivate(owner).get(0);
    }

    protected List<AbsoluteResourceLocation<PrivateResource>> getAllFilesInPrivate(UserIDAuth owner) {
        List<AbsoluteResourceLocation<PrivateResource>> files = listPrivate.list(
                ListRequest.forDefaultPrivate(owner, "./")
        ).collect(Collectors.toList());

        log.info("{} has {} in PRIVATE", owner.getUserID(), Log.secure(files));
        return files;
    }

    @SneakyThrows
    protected String readPrivateUsingPrivateKey(UserIDAuth user, PrivateResource location) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        InputStream dataStream = readFromPrivate.read(ReadRequest.forPrivate(user, location));

        ByteStreams.copy(dataStream, outputStream);
        String data = new String(outputStream.toByteArray());
        log.info("{} has {} in PRIVATE", user, Log.secure(data));

        return data;
    }

    protected void removeFromPrivate(UserIDAuth user, PrivateResource location) {
        removeFromPrivate.remove(RemoveRequest.forPrivate(user, location));
    }

    @SneakyThrows
    protected String readInboxUsingPrivateKey(UserIDAuth user, PrivateResource location) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        InputStream dataStream = readFromInbox.read(ReadRequest.forPrivate(user, location));

        ByteStreams.copy(dataStream, outputStream);
        String data = new String(outputStream.toByteArray());
        log.info("{} has {} in INBOX", user, Log.secure(data));

        return data;
    }

    protected AbsoluteResourceLocation<PrivateResource> getFirstFileInInbox(UserIDAuth inboxOwner) {
        return getAllFilesInInbox(inboxOwner).get(0);
    }

    protected List<AbsoluteResourceLocation<PrivateResource>> getAllFilesInInbox(UserIDAuth inboxOwner) {
        List<AbsoluteResourceLocation<PrivateResource>> files = listInbox.list(
                ListRequest.forDefaultPrivate(inboxOwner, "./")
        ).collect(Collectors.toList());

        log.info("{} has {} in INBOX", inboxOwner, Log.secure(files));
        return files;
    }

    protected void registerJohnAndJane(URI rootLocation) {
        john = registerUser("john", rootLocation);
        jane = registerUser("jane", rootLocation);
    }

    @SneakyThrows
    protected void sendToInbox(UserID to, String filename, String data) {
        OutputStream stream = writeToInbox.write(WriteRequest.forDefaultPublic(to, "./" + filename));
        stream.write(data.getBytes());
        stream.close();
        log.info("File {} sent to INBOX of user {}", Log.secure(filename), to);
    }

    protected void removeFromInbox(UserIDAuth inboxOwner, PrivateResource location) {
        removeFromInbox.remove(RemoveRequest.forPrivate(inboxOwner, location));
    }

    protected UserIDAuth registerUser(String userName, URI rootLocation) {
        UserIDAuth auth = new UserIDAuth(new UserID(userName), new ReadKeyPassword("secure-password " + userName));

        rootLocation = rootLocation.resolve(userName + "/");

        URI keyStoreUri = rootLocation.resolve("./" + PRIVATE_COMPONENT + "/keystore");
        log.info("User's keystore location: {}", Log.secure(keyStoreUri));
        URI inboxUri = rootLocation.resolve("./" + INBOX_COMPONENT + "/");
        log.info("User's inbox location: {}", Log.secure(inboxUri));

        profileRegistrationService.registerPublic(CreateUserPublicProfile.builder()
                .id(auth.getUserID())
                .inbox(access(inboxUri))
                .publicKeys(access(keyStoreUri))
                .build()
        );

        URI filesUri = rootLocation.resolve("./" + PRIVATE_FILES_COMPONENT + "/");
        log.info("User's files location: {}", Log.secure(filesUri));

        profileRegistrationService.registerPrivate(CreateUserPrivateProfile.builder()
                .id(auth)
                .privateStorage(accessPrivate(filesUri))
                .keystore(accessPrivate(keyStoreUri))
                .inboxWithWriteAccess(accessPrivate(inboxUri))
                .documentVersionStorage(accessPrivate(rootLocation.resolve("./" + VERSION_COMPONENT + "/")))
                .build()
        );

        log.info("Created user: {}", Log.secure(userName));
        return auth;
    }

    protected void removeUser(UserIDAuth userIDAuth) {
        profileRemovalService.deregister(userIDAuth);
        log.info("User deleted: {}", Log.secure(userIDAuth));
    }

    private AbsoluteResourceLocation<PublicResource> access(URI path) {
        return new AbsoluteResourceLocation<>(new DefaultPublicResource(path));
    }

    private AbsoluteResourceLocation<PrivateResource> accessPrivate(URI path) {
        return new AbsoluteResourceLocation<>(new DefaultPrivateResource(path, URI.create(""), URI.create("")));
    }

    protected UserIDAuth createJohnTestUser(int i) {
        UserID userName = new UserID("john_" + i);

        return new UserIDAuth(
                userName,
                new ReadKeyPassword("secure-password " + userName.getValue())
        );
    }

    protected void writeDataToFileForUser(UserIDAuth john, String filePathForWriting, byte[] bytes,
                                          CountDownLatch latch) throws IOException {
        WriteRequest<UserIDAuth, PrivateResource> writeRequest = WriteRequest.<UserIDAuth, PrivateResource>builder()
                .owner(john)
                .location(DefaultPrivateResource.forPrivate(URI.create(filePathForWriting)))
                .build();

        try (OutputStream os = writeToPrivate.write(writeRequest)) {
            os.write(bytes);
        }

        latch.countDown();
    }

}
