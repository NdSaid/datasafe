package de.adorsys.docusafe2.business.impl.keystore.service;

import de.adorsys.common.exceptions.BaseExceptionHandler;
import de.adorsys.common.utils.HexUtil;
import de.adorsys.docusafe2.business.api.keystore.KeyStoreService;
import de.adorsys.docusafe2.business.api.keystore.types.KeyID;
import de.adorsys.docusafe2.business.api.keystore.types.KeyStoreAccess;
import de.adorsys.docusafe2.business.api.keystore.types.KeyStoreAuth;
import de.adorsys.docusafe2.business.api.keystore.types.KeyStoreCreationConfig;
import de.adorsys.docusafe2.business.api.keystore.types.KeyStoreType;
import de.adorsys.docusafe2.business.api.keystore.types.PublicKeyIDWithPublicKey;
import de.adorsys.docusafe2.business.api.keystore.types.ReadKeyPassword;
import de.adorsys.docusafe2.business.api.keystore.types.SecretKeyIDWithKey;
import de.adorsys.docusafe2.business.impl.keystore.generator.KeyStoreGenerator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;

import javax.crypto.SecretKey;
import java.security.*;
import java.security.cert.Certificate;
import java.util.*;

@Slf4j
public class KeyStoreServiceImpl implements KeyStoreService {

    @Override
    public KeyStore createKeyStore(KeyStoreAuth keyStoreAuth,
                                   KeyStoreType keyStoreType,
                                   KeyStoreCreationConfig config) {
        try {
            log.debug("start create keystore ");
            if (config == null) {
                config = new KeyStoreCreationConfig(5, 5, 5);
            }
            // TODO, hier also statt der StoreID nun das
            String serverKeyPairAliasPrefix = HexUtil.convertBytesToHexString(UUID.randomUUID().toString().getBytes());
            log.debug("keystoreid = " + serverKeyPairAliasPrefix);
            {
                String realKeyStoreId = new String(HexUtil.convertHexStringToBytes(serverKeyPairAliasPrefix));
                log.debug("meaning of keystoreid = " + realKeyStoreId);
            }
            KeyStoreGenerator keyStoreGenerator = new KeyStoreGenerator(
                    config,
                    keyStoreType,
                    serverKeyPairAliasPrefix,
                    keyStoreAuth.getReadKeyPassword());
            KeyStore userKeyStore = keyStoreGenerator.generate();
            log.debug("finished create keystore ");
            return userKeyStore;
        } catch (Exception e) {
            throw BaseExceptionHandler.handle(e);
        }
    }

    @Override
    public List<PublicKeyIDWithPublicKey> getPublicKeys(KeyStoreAccess keyStoreAccess) {
        log.debug("get public keys");
        List<PublicKeyIDWithPublicKey> result = new ArrayList<>();
        KeyStore keyStore = keyStoreAccess.getKeyStore();
        try {
            for (Enumeration<String> keyAliases = keyStore.aliases(); keyAliases.hasMoreElements(); ) {
                final String keyAlias = keyAliases.nextElement();
                Certificate cert = keyStore.getCertificate(keyAlias);
                if (cert == null) continue; // skip
                result.add(new PublicKeyIDWithPublicKey(new KeyID(keyAlias), cert.getPublicKey()));
            }
        } catch (Exception e) {
            throw BaseExceptionHandler.handle(e);
        }

        return result;
    }

    @Override
    public PrivateKey getPrivateKey(KeyStoreAccess keyStoreAccess, KeyID keyID) {
        ReadKeyPassword readKeyPassword = keyStoreAccess.getKeyStoreAuth().getReadKeyPassword();
        KeyStore keyStore = keyStoreAccess.getKeyStore();
        PrivateKey privateKey;
        try {
            privateKey = (PrivateKey) keyStore.getKey(keyID.getValue(), readKeyPassword.getValue().toCharArray());
        } catch (Exception e) {
            throw BaseExceptionHandler.handle(e);
        }
        return privateKey;
    }

    @Override
    public SecretKey getSecretKey(KeyStoreAccess keyStoreAccess, KeyID keyID) {
        KeyStore keyStore = keyStoreAccess.getKeyStore();
        SecretKey key = null;
        try {
            key = (SecretKey) keyStore.getKey(keyID.getValue(), keyStoreAccess.getKeyStoreAuth().getReadKeyPassword().getValue().toCharArray());
        } catch (UnrecoverableKeyException | NoSuchAlgorithmException | KeyStoreException e) {
            e.printStackTrace();
        }

        return key;
    }

    @Override
    public SecretKeyIDWithKey getRandomSecretKeyID(KeyStoreAccess keyStoreAccess) {
        KeyStore keyStore = keyStoreAccess.getKeyStore();
        Key key = null;
        String randomAlias = null;
        try {
            Enumeration<String> aliases = keyStore.aliases();
            List<String> keyIDs = new ArrayList<>(Collections.list(aliases));
            int randomIndex = RandomUtils.nextInt(0, keyIDs.size());
            randomAlias = keyIDs.get(randomIndex);
            char[] password = keyStoreAccess.getKeyStoreAuth().getReadKeyPassword().getValue().toCharArray();
            key = keyStore.getKey(randomAlias, password);
        } catch (KeyStoreException | UnrecoverableKeyException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return new SecretKeyIDWithKey(new KeyID(randomAlias), (SecretKey) key);
    }
}
