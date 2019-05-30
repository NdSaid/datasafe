package de.adorsys.datasafe.directory.impl.profile.operations;

import com.google.common.io.ByteStreams;
import de.adorsys.datasafe.directory.api.profile.operations.ProfileRegistrationService;
import de.adorsys.datasafe.directory.api.profile.operations.ProfileRemovalService;
import de.adorsys.datasafe.directory.api.profile.operations.ProfileRetrievalService;
import de.adorsys.datasafe.directory.api.types.CreateUserPrivateProfile;
import de.adorsys.datasafe.directory.api.types.CreateUserPublicProfile;
import de.adorsys.datasafe.directory.api.types.UserPrivateProfile;
import de.adorsys.datasafe.directory.api.types.UserPublicProfile;
import de.adorsys.datasafe.directory.impl.profile.exceptions.UserNotFoundException;
import de.adorsys.datasafe.directory.impl.profile.serde.GsonSerde;
import de.adorsys.datasafe.encrypiton.api.keystore.KeyStoreService;
import de.adorsys.datasafe.encrypiton.api.types.UserID;
import de.adorsys.datasafe.encrypiton.api.types.UserIDAuth;
import de.adorsys.datasafe.encrypiton.api.types.keystore.*;
import de.adorsys.datasafe.storage.api.actions.*;
import de.adorsys.datasafe.types.api.actions.ListRequest;
import de.adorsys.datasafe.types.api.resource.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyStore;
import java.util.List;

// FIXME: it should be broken down.
/**
 * Profile operations for user profile located on DFS.
 */
@Slf4j
public class DFSBasedProfileStorageImpl implements
        ProfileRegistrationService,
        ProfileRetrievalService,
        ProfileRemovalService {

    private static final Uri PRIVATE = new Uri("./profiles/private/");
    private static final Uri PUBLIC = new Uri("./profiles/public/");

    private final StorageReadService readService;
    private final StorageWriteService writeService;
    private final StorageRemoveService removeService;
    private final StorageListService listService;
    private final StorageCheckService checkService;

    private final KeyStoreService keyStoreService;
    private final DFSSystem dfsSystem;
    private final GsonSerde serde;
    private final UserProfileCache userProfileCache;

    @Inject
    public DFSBasedProfileStorageImpl(StorageReadService readService, StorageWriteService writeService,
                                      StorageRemoveService removeService, StorageListService listService,
                                      StorageCheckService checkService, KeyStoreService keyStoreService,
                                      DFSSystem dfsSystem, GsonSerde serde, UserProfileCache userProfileCache) {
        this.readService = readService;
        this.writeService = writeService;
        this.removeService = removeService;
        this.listService = listService;
        this.checkService = checkService;
        this.keyStoreService = keyStoreService;
        this.dfsSystem = dfsSystem;
        this.serde = serde;
        this.userProfileCache = userProfileCache;
    }

    /**
     * Register users' public profile at the location specified by {@link DFSSystem}, overwrites it if exists.
     */
    @Override
    @SneakyThrows
    public void registerPublic(CreateUserPublicProfile profile) {
        try (OutputStream os = writeService.write(locatePublicProfile(profile.getId()))) {
            os.write(serde.toJson(profile.removeAccess()).getBytes());
        }
        log.debug("Register public {}", profile.getId());
    }

    /**
     * Register users' private profile at the location specified by {@link DFSSystem}, creates keystore and publishes
     * public keys, but only if keystore doesn't exist.
     */
    @Override
    @SneakyThrows
    public void registerPrivate(CreateUserPrivateProfile profile) {
        try (OutputStream os = writeService.write(locatePrivateProfile(profile.getId().getUserID()))) {
            os.write(serde.toJson(profile.removeAccess()).getBytes());
        }
        log.debug("Register private {}", profile.getId());

        if (checkService.objectExists(profile.getKeystore())) {
            return;
        }

        List<PublicKeyIDWithPublicKey> publicKeys = createKeyStore(
                profile.getId().getUserID(),
                dfsSystem.privateKeyStoreAuth(profile.getId()),
                profile.getKeystore()
        );

        publishPublicKeysIfNeeded(profile.getPublishPubKeysTo(), publicKeys);
    }

    /**
     * Removes users' public and private profile, keystore, public keys and INBOX and private files
     */
    @Override
    public void deregister(UserIDAuth userID) {
        if (!userExists(userID.getUserID())) {
            log.debug("User deregistation failed. User '{}' does not exist", userID);
            throw new UserNotFoundException("User not found: " + userID);
        }

        AbsoluteLocation<PrivateResource> privateStorage = privateProfile(userID).getPrivateStorage();
        removeStorageByLocation(userID, privateStorage);

        AbsoluteLocation<PrivateResource> inbox = privateProfile(userID).getInboxWithFullAccess();
        removeStorageByLocation(userID, inbox);

        removeService.remove(privateProfile(userID).getKeystore());
        removeService.remove(privateStorage);
        removeService.remove(inbox);
        removeService.remove(locatePrivateProfile(userID.getUserID()));
        removeService.remove(locatePublicProfile(userID.getUserID()));
        log.debug("Deregistered user {}", userID);
    }

    /**
     * Reads user public profile from DFS, uses {@link UserProfileCache} for caching it
     */
    @Override
    public UserPublicProfile publicProfile(UserID ofUser) {
        UserPublicProfile userPublicProfile = userProfileCache.getPublicProfile().computeIfAbsent(
                ofUser,
                id -> readProfile(locatePublicProfile(ofUser), UserPublicProfile.class)
        );
        log.debug("get public profile {} for user {}", userPublicProfile, ofUser);
        return userPublicProfile;
    }

    /**
     * Reads user private profile from DFS, uses {@link UserProfileCache} for caching it
     */
    @Override
    public UserPrivateProfile privateProfile(UserIDAuth ofUser) {
        UserPrivateProfile userPrivateProfile = userProfileCache.getPrivateProfile().computeIfAbsent(
                ofUser.getUserID(),
                id -> readProfile(locatePrivateProfile(ofUser.getUserID()), UserPrivateProfile.class)
        );
        log.debug("get private profile {} for user {}", userPrivateProfile, ofUser);
        return userPrivateProfile;
    }

    /**
     * Checks if user exists by validating that his both public and private profile files do exist.
     */
    @Override
    public boolean userExists(UserID ofUser) {
        return checkService.objectExists(locatePrivateProfile(ofUser)) &&
                checkService.objectExists(locatePublicProfile(ofUser));
    }

    private void removeStorageByLocation(UserIDAuth userID, AbsoluteLocation<PrivateResource> privateStorage) {
        listService.list(new ListRequest<>(userID, privateStorage).getLocation())
                .forEach(removeService::remove);
    }

    @SneakyThrows
    private <T extends ResourceLocation<T>> List<PublicKeyIDWithPublicKey> createKeyStore(
            UserID forUser, KeyStoreAuth auth, AbsoluteLocation<T> keystore) {
        KeyStore keystoreBlob = keyStoreService.createKeyStore(
                auth,
                KeyStoreType.DEFAULT,
                new KeyStoreCreationConfig(1, 1)
        );

        try (OutputStream os = writeService.write(keystore)) {
            os.write(keyStoreService.serialize(keystoreBlob, forUser.getValue(), auth.getReadStorePassword()));
        }
        log.debug("Keystore created for user {} in path {}", forUser, keystore);

        return keyStoreService.getPublicKeys(new KeyStoreAccess(keystoreBlob, auth));
    }

    @SneakyThrows
    private void publishPublicKeysIfNeeded(AbsoluteLocation publishTo,
                                           List<PublicKeyIDWithPublicKey> publicKeys) {

        if (null != publishTo && !checkService.objectExists(publishTo)) {
            try (OutputStream os = writeService.write(publishTo)) {
                os.write(serde.toJson(publicKeys).getBytes());
            }
        }
    }

    @SneakyThrows
    private <T> T readProfile(AbsoluteLocation resource, Class<T> clazz) {
        try (InputStream is = readService.read(resource)) {
            log.debug("read profile {}", resource.location().getPath());
            return serde.fromJson(new String(ByteStreams.toByteArray(is)), clazz);
        }
    }

    private AbsoluteLocation<PrivateResource> locatePrivateProfile(UserID ofUser) {
        return new AbsoluteLocation<>(
                new BasePrivateResource(PRIVATE.resolve(ofUser.getValue())).resolveFrom(dfsSystem.dfsRoot())
        );
    }

    private AbsoluteLocation<PublicResource> locatePublicProfile(UserID ofUser) {
        return new AbsoluteLocation<>(
                new BasePublicResource(PUBLIC.resolve(ofUser.getValue())).resolveFrom(dfsSystem.dfsRoot())
        );
    }
}