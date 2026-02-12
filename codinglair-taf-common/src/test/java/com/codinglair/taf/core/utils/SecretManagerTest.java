package com.codinglair.taf.core.utils;

import com.codinglair.taf.core.utils.secret.impl.JasyptSecretManager;
import com.codinglair.taf.core.utils.secret.abstraction.SecretManager;
import com.codinglair.taf.core.utils.secret.factory.SecretManagerFactory;
import org.junit.Test;
import org.junit.Assert;

public class SecretManagerTest {
    @Test
    public void testDecryption(){
        String encryptedString = "ENC(CyuPBdd7HLcoBoPPwJ+BHdbEIjKLg1ansYrYPsBtdQCZGZHhaBI04I4EnZZdG24z)";
        String decryptedString = SecretManagerFactory.create(JasyptSecretManager.class.getName())
                .decrypt(encryptedString);
        Assert.assertEquals("pasword123", decryptedString);
    }

    @Test
    public void testIsEncrypted() {
        String encryptedString = "ENC(CyuPBdd7HLcoBoPPwJ+BHdbEIjKLg1ansYrYPsBtdQCZGZHhaBI04I4EnZZdG24z)";
        SecretManager sm = SecretManagerFactory.create(JasyptSecretManager.class.getName());
        boolean isEncrypted = sm.isEncrypted(encryptedString);
        Assert.assertTrue(isEncrypted);
        Assert.assertFalse( sm.isEncrypted("Random_String"));
    }
}
