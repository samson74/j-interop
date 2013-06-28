/**
* Donated by Jarapac (http://jarapac.sourceforge.net/) and released under EPL.
* 
* j-Interop (Pure Java implementation of DCOM protocol)
*     
* Copyright (c) 2013 Vikram Roopchand
* 
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
* Vikram Roopchand  - Moving to EPL from LGPL v1.
*  
*/


package rpc.security.ntlm;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Properties;
import java.util.Random;

import jcifs.Config;
import jcifs.ntlmssp.NtlmFlags;
import jcifs.ntlmssp.NtlmMessage;
import jcifs.ntlmssp.Type1Message;
import jcifs.ntlmssp.Type2Message;
import jcifs.ntlmssp.Type3Message;
import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.util.Encdec;
import net.sourceforge.jtds.util.SSPIJNIClient;
import rpc.Security;

public class NtlmAuthentication {

    public static final int AUTHENTICATION_SERVICE_NTLM = 10;



    private static final boolean UNICODE_SUPPORTED =
            Config.getBoolean("jcifs.smb.client.useUnicode", true);


    private static final int BASIC_FLAGS = NtlmFlags.NTLMSSP_REQUEST_TARGET | NtlmFlags.NTLMSSP_NEGOTIATE_NTLM |
            NtlmFlags.NTLMSSP_NEGOTIATE_OEM |
                    NtlmFlags.NTLMSSP_NEGOTIATE_ALWAYS_SIGN |
                            (UNICODE_SUPPORTED ?
                                    NtlmFlags.NTLMSSP_NEGOTIATE_UNICODE : 0);


    private Security security;

    protected Properties properties;

    private NtlmPasswordAuthentication credentials;

    private AuthenticationSource authenticationSource;

    private boolean lanManagerKey;

    private boolean seal;

    private boolean sign;

    private boolean keyExchange;

    //we always go for 128
    private int keyLength = 128;

    private boolean useNtlm2sessionsecurity = false;
    
    private boolean useNtlmV2 = false;

    private boolean useSSO = false;
    
    private static final Random RANDOM = new Random();

    private final SSPIJNIClient jniClient;
    
    public NtlmAuthentication(Properties properties) {
        this.properties = properties;
        String domain = null;
        String user = null;
        String password = null;
        if (properties != null) {
            lanManagerKey = Boolean.valueOf(properties.getProperty(
                    "rpc.ntlm.lanManagerKey")).booleanValue();
            seal = Boolean.valueOf(properties.getProperty(
                    "rpc.ntlm.seal")).booleanValue();
            sign = seal ? true : Boolean.valueOf(properties.getProperty(
                    "rpc.ntlm.sign")).booleanValue();
            keyExchange = Boolean.valueOf(properties.getProperty(
                    "rpc.ntlm.keyExchange")).booleanValue();
            String keyLength = properties.getProperty("rpc.ntlm.keyLength");
            if (keyLength != null) {
                try {
                    this.keyLength = Integer.parseInt(keyLength);
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("Invalid key length: " +
                            keyLength);
                }
            }

            useNtlm2sessionsecurity = Boolean.valueOf(properties.getProperty(
            "rpc.ntlm.ntlm2")).booleanValue();
            useNtlmV2 = Boolean.valueOf(properties.getProperty("rpc.ntlm.ntlmv2")).booleanValue();
            useSSO = Boolean.valueOf(properties.getProperty("rpc.ntlm.sso")).booleanValue();
            domain = properties.getProperty("rpc.ntlm.domain");
            user = properties.getProperty(Security.USERNAME);
            password = properties.getProperty(Security.PASSWORD);
        }
        
        if (useSSO)
        {
			jniClient = SSPIJNIClient.getInstance();
        }
        else
    	{
        	jniClient = null;
        	credentials = new NtlmPasswordAuthentication(domain, user, password);
    	}


    }

    public Security getSecurity() throws IOException
    {
       return security;
    }

    protected AuthenticationSource getAuthenticationSource() {
        if (authenticationSource != null) return authenticationSource;
        String sourceClass = (properties != null) ?
                properties.getProperty("rpc.ntlm.authenticationSource") : null;
        if (sourceClass == null) {
            return (authenticationSource =
                    AuthenticationSource.getDefaultInstance());
        }
        try {
            return (authenticationSource = (AuthenticationSource)
                    Class.forName(sourceClass).newInstance());
        } catch (Exception ex) {
            throw new IllegalArgumentException(
                    "Invalid authentication source: " + ex);
        }
    }

    private int getDefaultFlags() {
        int flags = BASIC_FLAGS;
        if (lanManagerKey) flags |= NtlmFlags.NTLMSSP_NEGOTIATE_LM_KEY;
        if (sign) flags |= NtlmFlags.NTLMSSP_NEGOTIATE_SIGN;
        if (seal) flags |= NtlmFlags.NTLMSSP_NEGOTIATE_SEAL;
        if (keyExchange) flags |= NtlmFlags.NTLMSSP_NEGOTIATE_KEY_EXCH;
        if (keyLength >= 56) flags |= NtlmFlags.NTLMSSP_NEGOTIATE_56;
        if (keyLength >= 128) flags |= NtlmFlags.NTLMSSP_NEGOTIATE_128;
        //We always negotiate for NTLM2 session security
//        if (useNtlm2sessionsecurity)
        {
        	flags |= NtlmFlags.NTLMSSP_NEGOTIATE_NTLM2;
        }

        return flags;
    }

    private int adjustFlags(int flags) {
        if (UNICODE_SUPPORTED &&
                ((flags & NtlmFlags.NTLMSSP_NEGOTIATE_UNICODE) != 0)) {
            flags &= ~NtlmFlags.NTLMSSP_NEGOTIATE_OEM;
            flags |= NtlmFlags.NTLMSSP_NEGOTIATE_UNICODE;
        } else {
            flags &= ~NtlmFlags.NTLMSSP_NEGOTIATE_UNICODE;
            flags |= NtlmFlags.NTLMSSP_NEGOTIATE_OEM;
        }
        if (!lanManagerKey) flags &= ~NtlmFlags.NTLMSSP_NEGOTIATE_LM_KEY;
        if (!(sign || seal)) flags &= ~NtlmFlags.NTLMSSP_NEGOTIATE_SIGN;
        if (!seal) flags &= ~NtlmFlags.NTLMSSP_NEGOTIATE_SEAL;
        if (!keyExchange) flags &= ~NtlmFlags.NTLMSSP_NEGOTIATE_KEY_EXCH;
        if (keyLength < 128) flags &= ~NtlmFlags.NTLMSSP_NEGOTIATE_128;
        if (keyLength < 56) flags &= ~NtlmFlags.NTLMSSP_NEGOTIATE_56;
//        if (!useNtlm2sessionsecurity)
//        {
//        	flags &= ~NtlmFlags.NTLMSSP_NEGOTIATE_NTLM2;
//        }
        return flags;
    }

    public Type1Message createType1() throws IOException {
    	
    	if (useSSO)
    	{
    		byte[] ntlmMessage = jniClient.invokePrepareSSORequest();
    		Type1Message type1Message = new Type1Message(ntlmMessage);
    		type1Message.setFlags(getDefaultFlags());
    		return type1Message;
    	}
    	else
    	{
    		int flags = getDefaultFlags();
    		return new Type1Message(flags, credentials.getDomain(),Type1Message.getDefaultWorkstation());
    	}
    }

    public Type2Message createType2(Type1Message type1) throws IOException {
        int flags;
        if (type1 == null) {
            flags = getDefaultFlags();
        } else {
            flags = adjustFlags(type1.getFlags());
        }
        flags |= 0x00020000; //challenge accept response flag

        Type2Message type2Message = new Type2Message(flags,
                    new byte[]{1,2,3,4,5,6,7,8}, //generate our own, since SMB will throw exception here
                    credentials.getDomain());

        return type2Message;
    }
    
    public Type3Message createType3(Type2Message type2) throws IOException {
    	if (useSSO)
    	{
    		byte[] ntlmMessage = type2.toByteArray();        
    		byte[] ret = jniClient.invokePrepareSSOSubmit(ntlmMessage);
    		Type3Message message = new Type3Message(ret);
    		int flags = type2.getFlags();
	        if ((flags & NtlmFlags.NTLMSSP_NEGOTIATE_DATAGRAM_STYLE) != 0)
	        {
	            flags = adjustFlags(flags);
	            flags &= ~0x00020000;
	        }
    		message.setFlags(flags);
    		return message;
    	}
    	else
    	{
	    	int flags = type2.getFlags();
	        if ((flags & NtlmFlags.NTLMSSP_NEGOTIATE_DATAGRAM_STYLE) != 0)
	        {
	            flags = adjustFlags(flags);
	            flags &= ~0x00020000;
	        }
	
	        Type3Message type3 = null;
	        
	        byte[] clientNonce = new byte[8];
	        byte[] blob = null;
	        
	        String target = null;//getTargetFromTargetInformation(type2.getTargetInformation());
	        
	        if (target == null)
	        {
	        	target = credentials.getDomain().toUpperCase();
	        	if (target.equals(""))
	        	{
	        		target = getTargetFromTargetInformation(type2.getTargetInformation());
	        	}
	        }
	        
	        if (useNtlmV2)
	        {
	        	RANDOM.nextBytes(clientNonce);
	        	try {
	        		byte[] lmv2Response = Responses.getLMv2Response(target, credentials.getUsername(), credentials.getPassword(), 
	            			type2.getChallenge(), clientNonce);
	        		byte[][] retval = Responses.getNTLMv2Response(target, credentials.getUsername(), credentials.getPassword(), type2.getTargetInformation(), type2.getChallenge(), clientNonce);
	        		byte[] ntlmv2Response = retval[0];
	        		blob = retval[1];
	        		type3 = new Type3Message(flags, lmv2Response, ntlmv2Response,
	        				target, credentials.getUsername(),
	 	                    Type3Message.getDefaultWorkstation());
				} catch (Exception e)
				{
					throw new RuntimeException("Exception occured while forming NTLMv2 Type3Response",e);
				}
				
	        }
	        else
	        if ((flags & NtlmFlags.NTLMSSP_NEGOTIATE_NTLM2) != 0) //NTLM2 Session security response
	    	{
	    		flags = adjustFlags(flags);
	            flags &= ~0x00020000;
	        	//flags =  0xe2888235;
	            byte[] challenge = type2.getChallenge();
	        	//LMReponse is 24 bytes. 8 byte random client nonce and the rest is null padded.
	            byte[] lmResponse = new byte[24];
	            
	            RANDOM.nextBytes(clientNonce);
	            System.arraycopy(clientNonce, 0, lmResponse, 0, clientNonce.length);
	            byte[] ntResponse;
				try {
					ntResponse = Responses.getNTLM2SessionResponse(credentials.getPassword(), challenge, clientNonce);
				} catch (Exception e)
				{
					throw new RuntimeException("Exception occured while forming Session Security Type3Response",e);
				}
	
	            type3 = new Type3Message(flags, lmResponse, ntResponse,
	            		target, credentials.getUsername(),
	                    Type3Message.getDefaultWorkstation());
	    	}
	    	else //Plain NTLMv1 response
	    	{
	    		byte[] challenge = type2.getChallenge();
	            byte[] lmResponse = NtlmPasswordAuthentication.getPreNTLMResponse(
	                    credentials.getPassword(), challenge);
	            byte[] ntResponse = NtlmPasswordAuthentication.getNTLMResponse(
	                    credentials.getPassword(), challenge);
	            type3 = new Type3Message(flags, lmResponse, ntResponse,
	            		target, credentials.getUsername(),
	                            Type3Message.getDefaultWorkstation());
	            if ((flags & NtlmFlags.NTLMSSP_NEGOTIATE_KEY_EXCH) != 0) {
	            	throw new RuntimeException("Key Exchange not supported by Library !");
	            }	
	    	}
	        //we have to now form lmv2 and ntlmv2 response with regards to the session security
	        //the type3message also has to be altered
	        if (useNtlm2sessionsecurity && (flags & NtlmFlags.NTLMSSP_NEGOTIATE_NTLM2) != 0)
	        {
	        	NTLMKeyFactory ntlmKeyFactory = new NTLMKeyFactory();
	        	byte[] userSessionKey;
	        	if (useNtlmV2)
	        	{
	        		try {
	        			userSessionKey = ntlmKeyFactory.getNTLMv2UserSessionKey(target, credentials.getUsername(), credentials.getPassword(), type2.getChallenge(), blob);
					} catch (Exception e) {
						throw new RuntimeException("Exception occured while forming NTLMv2 with NTLM2 Session Security for Type3Response",e);
					}
	        	}
	        	else
	        	{
	                //now create the key for the session
	                //this key will be used to RC4 a 16 byte random key and set to the type3 message
	                byte[] servernonce = new byte[16];
	                System.arraycopy(type2.getChallenge(), 0, servernonce, 0, type2.getChallenge().length);
	                System.arraycopy(clientNonce, 0, servernonce, 8, clientNonce.length);
	                try {
	    				userSessionKey = ntlmKeyFactory.getNTLM2SessionResponseUserSessionKey(credentials.getPassword(), servernonce);
	    			} catch (Exception e)
	    			{
	    				throw new RuntimeException("Exception occured while forming Session Security for Type3Response",e);
	    			}
	                
	        	}
	        	
	        	try {
					//now RC4 encrypt a random 16 byte key
					byte[] secondayMasterKey = ntlmKeyFactory.getSecondarySessionKey();
					type3.setSessionKey(ntlmKeyFactory.encryptSecondarySessionKey(secondayMasterKey, userSessionKey));
					security = (Security) new Ntlm1(flags, secondayMasterKey,false);
				} catch (Exception e)
				{
					throw new RuntimeException("Exception occured while forming Session Security for Type3Response",e);
				}
	        }
	
	        return type3;
    	}
    }

    private String getTargetFromTargetInformation(byte[] targetInformation)
    {
    	 String target = null;
         
         int i = 0;
         while (i < targetInformation.length)
         {
             switch(Encdec.dec_uint16le(targetInformation, i))
             {
                 case 1: //Server name
                     i++;
                     i++; //advance two bytes
                     int length = Encdec.dec_uint16le(targetInformation, i);
                     i++;
                     i++;//advance two bytes
                     byte[] domainb = new byte[length];
                     System.arraycopy(targetInformation, i, domainb, 0, length);
					 try {
					 	 target = new String(domainb,"UTF-16LE");
					 } catch (UnsupportedEncodingException e) {
					     return null;
					 }
                     i = i + length;
                     i = targetInformation.length;
                     break;
                 default: //skip bytes   
                     i++;
                     i++; //advance two bytes
                     length = Encdec.dec_uint16le(targetInformation, i);
                     i++;
                     i++;//advance two bytes
                     i  = i + length;
             }
         }
         
         return target;
    }
    void createSecurityWhenServer(NtlmMessage type3)
    {
    	Type3Message type3Message = (Type3Message)type3;
    	//two things here...check for anonymous , in that case the user response key is new byte[16].
    	//in case anonymous has not been sent then create the key using credentials.
    	int flags = type3Message.getFlags();
    	NTLMKeyFactory ntlmKeyFactory = new NTLMKeyFactory();
    	byte[] secondayMasterKey;
    	byte[] sessionResponseUserSessionKey = null;
    	if (type3Message.getFlag(0x00000800))//anonymous flag
    	{
    		//if it is anonymous the user session key is new byte[16];
    		sessionResponseUserSessionKey = new byte[16];
    	}
    	else if(useNtlmV2) 
    	{
    		//TODO this needs to be checked here since the key logic will be totally different 
    		//and we have to get the key out of Type3 message response (blob of the NTLMv2 response.)
    		int h = 0;
    	}
    	else
    	{
    		 //now create the key for the session
            //this key will be used to RC4 a 16 byte random key and set to the type3 message
            byte[] servernonce = new byte[16];
            byte[] challenge = new byte[]{1,2,3,4,5,6,7,8}; //challenge is fixed
            System.arraycopy(challenge, 0, servernonce, 0, challenge.length);
            System.arraycopy(type3Message.getLMResponse(), 0, servernonce, 8, 8);//first 8 bytes only , the rest are all 0x00 and not required.
            try {
				sessionResponseUserSessionKey = ntlmKeyFactory.getNTLM2SessionResponseUserSessionKey(credentials.getPassword(), servernonce);
			} catch (Exception e) {
				throw new RuntimeException("Exception occured while forming Session Security from Type3 AUTH",e);
			}
    	}

    	try {
			//now RC4 decrypt the session key
    		secondayMasterKey = ntlmKeyFactory.decryptSecondarySessionKey(type3Message.getSessionKey(), sessionResponseUserSessionKey);
			security = (Security) new Ntlm1(flags, secondayMasterKey,true);
		} catch (Exception e)
		{
			throw new RuntimeException("Exception occured while forming Session Security Type3Response",e);
		}
    }

}
