package de.adorsys.docusafe2.business.impl.dfscredentialservice;

import de.adorsys.dfs.connection.api.complextypes.BucketPath;
import de.adorsys.docusafe2.business.api.dfscredentialservice.DFSCredentialService;
import de.adorsys.docusafe2.business.api.keystore.types.ReadKeyPassword;
import de.adorsys.docusafe2.business.api.types.DFSCredentials;
import de.adorsys.docusafe2.business.api.types.UserID;
import de.adorsys.docusafe2.business.api.types.UserIDAuth;
import org.junit.*;
import org.junit.runners.MethodSorters;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

public class DFSCredentialServiceTest {

    public static final String DFS_FS_PATH = "./target/filesystemstorage"; //default path for FS DFS implementation
    public static final String SYSTEM_DFS_DIRECTORY_NAME = "system-dfs";
    private DFSCredentialService dfsCredentialService = new DFSCredentialServiceImpl();

    private DFSCredentials dfsCredentialsExpected = new DFSCredentials();

    @Before
    public void setUp() {
        dfsCredentialsExpected.setS3accessKey("s3AccessKey");
        dfsCredentialsExpected.setSecret("secret");
    }

    @Test
    public void testSuccessPathWhenRegisterDFS() {
        UserIDAuth userIDAuth = getTestUser();

        dfsCredentialService.registerDFS(dfsCredentialsExpected, userIDAuth);

        DFSCredentials dfsCredentialsActual = dfsCredentialService.getDFSCredentials(userIDAuth);

        Assert.assertEquals(dfsCredentialsExpected.getS3accessKey(), dfsCredentialsActual.getS3accessKey());
        Assert.assertEquals(dfsCredentialsExpected.getSecret(), dfsCredentialsActual.getSecret());
    }

    @Test
    public void testDFSCredentialsSaved() {
        UserIDAuth userIDAuth = new UserIDAuth();
        userIDAuth.setReadKeyPassword(new ReadKeyPassword("pass"));
        UserID testUserID = new UserID("userID");
        userIDAuth.setUserID(testUserID);

        dfsCredentialService.registerDFS(dfsCredentialsExpected, userIDAuth);

        Assert.assertTrue(new File(
          DFS_FS_PATH + BucketPath.BUCKET_SEPARATOR +
                    SYSTEM_DFS_DIRECTORY_NAME + BucketPath.BUCKET_SEPARATOR +
                    testUserID.getValue()
           ).exists()
        );
    }

    @Test(expected = DFSCredentialException.class)
    public void testAlreadyRegisteredUser() {
        UserIDAuth userIDAuth = getTestUser();

        dfsCredentialService.registerDFS(dfsCredentialsExpected, userIDAuth);
        dfsCredentialService.registerDFS(dfsCredentialsExpected, userIDAuth);
    }

    private UserIDAuth getTestUser() {
        UserIDAuth userIDAuth = new UserIDAuth();
        userIDAuth.setReadKeyPassword(new ReadKeyPassword("pass"));
        userIDAuth.setUserID(new UserID("testUserID"));
        return userIDAuth;
    }

    @After
    public void cleanUp() throws IOException {
        Files.walk(Paths.get(DFS_FS_PATH))
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }

}
