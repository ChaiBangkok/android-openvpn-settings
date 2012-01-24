/**
 * Copyright 2009 Friedrich Schäuffelhut
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */
package de.schaeuffelhut.android.openvpn.util;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import com.bugsense.trace.BugSense;
import com.bugsense.trace.BugSenseHandler;

import android.util.Log;


public class Shell extends Thread
{
	private final static boolean LOCAL_LOGD = true;
	
	public final static boolean SU = true;
	public final static boolean SH = false;
	
	
	private final String mSh;
	private final String mSu;
	
	private final String mTag;
	private final String mCmd;
	private final boolean mRoot;

	private Process mShellProcess;
	private LoggerThread mStdoutLogger;
	private LoggerThread mStderrLogger;
	private PrintStream stdin;
	
	public Shell(String tag, String cmd, boolean root)
	{
		super( tag + "-stdin" );
		mTag = tag;
		mCmd = cmd;
		mRoot = root;
		
		mSh = findBinary( "sh" );
		mSu = findBinary( "su" );
	}

	static String findBinary(String executable) {
		for (String bin : new String[]{"/system/bin/", "/system/xbin/"}) {
			String path = bin+executable;
			if ( new File( path ).exists() )
				return path;
		}
		return executable;
		// causes Force Close on unrooted phones
//		throw new RuntimeException( "executable not found: " + executable );
	}

	public final void run()
	{
		final ProcessBuilder shellBuilder = new ProcessBuilder( mRoot ? mSu : mSh );

		if ( LOCAL_LOGD )
			Log.d( mTag, String.format( 
					"invoking external process: %s", 
					Util.join( shellBuilder.command(), ' ' )
			));
		
		onBeforeExecute();
					
		try
		{
			mShellProcess = shellBuilder.start();
		}
		catch (IOException e)
		{
			// Something went fundamentally wrong as either
			// /system/bin/sh or /system/bin/su could not be
			// found. This is a border line case which really
			// should have been already handled before run()
			// was called!
			
			Log.e( mTag, String.format( 
					"invoking external process: %s", 
					Util.join( shellBuilder.command(), ' ' ),
					e
			));
			BugSenseHandler.log(
					String.format( 
							"invoking external process: %s", 
							Util.join( shellBuilder.command(), ' ' )
					),
					e
			);
			
			onExecuteFailed( e );
			
			return;
		}

		mStdoutLogger = new LoggerThread( mTag+"-stdout", mShellProcess.getInputStream(), true ){
			@Override
			protected void onLogLine(String line) {
				onStdout(line);
			}
		};
		mStdoutLogger.start();

		mStderrLogger = new LoggerThread( mTag+"-stderr", mShellProcess.getErrorStream(), true ){
			@Override
			protected void onLogLine(String line) {
				onStderr(line);
			}
		};
		mStderrLogger.start();

		stdin = new PrintStream( mShellProcess.getOutputStream() );

		if ( LOCAL_LOGD )
			Log.d( mTag, String.format( "invoking command line: %s", mCmd ) );
		stdin.println( mCmd );
		stdin.flush();
		onCmdStarted();
		Util.closeQuietly( stdin );
		
		try { joinLoggers(); } catch (InterruptedException e) {Log.e( mTag, "joining loggers", e);}
		int exitCode = waitForQuietly();

		onCmdTerminated( exitCode );
	}
	
	protected void onBeforeExecute() {
		//overwrite if desired
	}

	protected void onExecuteFailed(IOException e) {
		//overwrite if desired
		// if this method is called either su or sh was not found or may not be executed.
	}
	
	protected void onStdout(String line) {
		//overwrite if desired
	}

	protected void onStderr(String line) {
		//overwrite if desired
	}
	
	protected void onCmdStarted() {
		//overwrite if desired
	}

	protected void onCmdTerminated(int exitCode){
		//overwrite if desired
	}

	private final void joinLoggers() throws InterruptedException
	{
		if ( mStdoutLogger != null )
			mStdoutLogger.join();
		
		if ( mStderrLogger != null )
			mStderrLogger.join();
	}
	
	private final int waitForQuietly()
	{
		return Util.waitForQuietly( mShellProcess );
	}
}
