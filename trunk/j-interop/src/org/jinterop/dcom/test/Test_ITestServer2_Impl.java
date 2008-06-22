package org.jinterop.dcom.test;

import java.net.UnknownHostException;

import org.jinterop.dcom.common.JIException;
import org.jinterop.dcom.core.IJIComObject;
import org.jinterop.dcom.core.JIComServer;
import org.jinterop.dcom.core.JIFlags;
import org.jinterop.dcom.core.JIInterfaceDefinition;
import org.jinterop.dcom.core.JIJavaCoClass;
import org.jinterop.dcom.core.JIMethodDescriptor;
import org.jinterop.dcom.core.JIParameterObject;
import org.jinterop.dcom.core.JIProgId;
import org.jinterop.dcom.core.JISession;
import org.jinterop.dcom.core.JIString;
import org.jinterop.dcom.core.JIVariant;
import org.jinterop.dcom.impls.JIObjectFactory;
import org.jinterop.dcom.impls.automation.IJIDispatch;

public class Test_ITestServer2_Impl {

	public void Execute(JIString str)
	{
		System.out.println(str.getString());
	}
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		if (args.length < 4)
	    {
	    	System.out.println("Please provide address domain username password");
	    	return;
	    }
		
		
		
		try {
			JISession session1 = JISession.createSession(args[1],args[2],args[3]);
			JISession session2 = JISession.createSession(args[1],args[2],args[3]);
			JIComServer testServer1 = new JIComServer(JIProgId.valueOf(session1,"TestJavaServer.TestServer1"),args[0],session1);
			IJIComObject unkTestServer1 = testServer1.createInstance();
			IJIDispatch dispatch1 = (IJIDispatch)JIObjectFactory.narrowObject(unkTestServer1.queryInterface(IJIDispatch.IID));;
			
			//First lets call the ITestServer1.Call_TestServer2_Java using the Dispatch interface
			//Acquire a reference to ITestServer2
			JIComServer testServer2 = new JIComServer(JIProgId.valueOf(session2,"TestJavaServer.TestServer2"),args[0],session2);
			IJIComObject unkTestServer2 = testServer2.createInstance();
			//Get the interface pointer to ITestServer2
			IJIComObject iTestServer2 = (IJIComObject)unkTestServer2.queryInterface("9CCC5120-457D-49F3-8113-90F7E97B54A7");
			//Send it to ITestServer.Call_TestServer2_Java via IDispatch of ITestServer1.
			dispatch1.callMethod("Call_TestServer2_Java", new Object[]{new JIVariant(iTestServer2)});
			
			
			//Now for the Java Implementation of ITestServer2 interface (from the type library or IDL)  
			//IID of ITestServer2 interface
			JIInterfaceDefinition interfaceDefinition = new JIInterfaceDefinition("9CCC5120-457D-49F3-8113-90F7E97B54A7");
			//lets define the method "Execute" now. Please note that either this should be in the same order as defined in IDL
			//or use the addInParamAsObject with opnum parameter function.
			JIParameterObject parameterObject = new JIParameterObject();
			parameterObject.addInParamAsObject(new JIString(JIFlags.FLAG_REPRESENTATION_STRING_BSTR),JIFlags.FLAG_REPRESENTATION_STRING_BSTR);
			JIMethodDescriptor methodDescriptor = new JIMethodDescriptor("Execute",1,parameterObject);
			interfaceDefinition.addMethodDescriptor(methodDescriptor);
			//Create the Java Server class. This contains the instance to be called by the COM Server ITestServer1.
			JIJavaCoClass _testServer2 = new JIJavaCoClass(interfaceDefinition,new Test_ITestServer2_Impl());
			//Get a interface pointer to the Java CO Class. The template could be any IJIComObject since only the session is reused.
			IJIComObject __testServer2 = JIObjectFactory.buildObject(session1,_testServer2); 
			//Call our Java server. The same message should be printed on the Java console.
			dispatch1.callMethod("Call_TestServer2_Java", new Object[]{new JIVariant(__testServer2)});
			
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (JIException e) {
			e.printStackTrace();
		}
		

	}

}