package de.adorsys.datasafe.encrypiton.impl.keystore.generator;

import lombok.experimental.UtilityClass;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.Provider;
import java.security.Security;

@UtilityClass
class ProviderUtils {

    static final Provider bcProvider = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME);
}
