
package com.actuate.tool.development.installer.task;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.io.StringWriter;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.ProjectHelper;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;
import org.sf.feeling.swt.win32.extension.io.FileSystem;
import org.sf.feeling.swt.win32.extension.shell.Windows;

import com.actuate.tool.development.installer.model.IPortalViewerData;
import com.actuate.tool.development.installer.model.Modules;
import com.actuate.tool.development.installer.util.FileUtil;
import com.actuate.tool.development.installer.util.LogUtil;
import com.actuate.tool.development.installer.util.UIUtil;

public class SyncIPortalWorkspace
{

	private IPortalViewerData data;

	public SyncIPortalWorkspace( IPortalViewerData data )
	{
		this.data = data;
	}

	public void execute( final IProgressMonitor monitor )
	{
		if ( data == null )
			return;

		monitor.beginTask( "Total 9 steps", IProgressMonitor.UNKNOWN );

		final int[] step = new int[1];
		final String[] stepDetail = new String[1];
		final String[] originRoot = new String[1];
		try
		{
			monitor.subTask( "[Step "
					+ ++step[0]
					+ "] Checking the perforce connection settings..." );
			stepDetail[0] = "Check the perforce connection settings";

			final String result = checkP4ConnectionSettings( );

			if ( result != null )
			{
				Display.getDefault( ).syncExec( new Runnable( ) {

					public void run( )
					{
						MessageDialog.openError( null, "Error", result );
						Windows.flashWindow( UIUtil.getShell( ).handle, false );
					}
				} );
				return;
			}

			monitor.subTask( "[Step "
					+ ++step[0]
					+ "] Updating the perforce client workspace specification from "
					+ data.getServer( ) );
			stepDetail[0] = "Update the perforce client workspace specification from "
					+ data.getServer( );

			originRoot[0] = updateClientSpecification( getTempFile( "specification.txt" ),
					data.getRoot( ) );

			if ( originRoot[0] == null )
			{
				Display.getDefault( ).syncExec( new Runnable( ) {

					public void run( )
					{
						Windows.flashWindow( UIUtil.getShell( ).handle, false );
					}
				} );
				return;
			}

			monitor.subTask( "[Step "
					+ ++step[0]
					+ "] Synchronizing the iPortal Viewer workspace..." );
			stepDetail[0] = "Synchronize the iPortal Viewer workspace";

			if ( originRoot[0] != null )
			{
				synciPortal( monitor, step );
			}

			monitor.subTask( "[Step "
					+ ++step[0]
					+ "] Resetting the perforce client workspace specification from "
					+ data.getServer( ) );
			stepDetail[0] = "Reset the perforce client workspace specification from "
					+ data.getServer( );

			if ( originRoot[0] != null )
			{
				updateClientSpecification( getTempFile( "specification.txt",
						".txt" ),
						originRoot[0] );

				originRoot[0] = null;
			}

			Project p = new Project( );
			p.setBasedir( FileSystem.getCurrentDirectory( ) );

			final DefaultLogger consoleLogger = new DefaultLogger( );
			consoleLogger.setErrorPrintStream( System.err );
			consoleLogger.setOutputPrintStream( System.out );
			consoleLogger.setMessageOutputLevel( Project.MSG_INFO );
			p.addBuildListener( consoleLogger );

			monitor.subTask( "[Step "
					+ ++step[0]
					+ "] Initializing the iPortal Viewer workspace replacement task..." );
			stepDetail[0] = "Initializing the iPortal Viewer workspace replacement task";

			File initFile = getAntFile( "/templates/Init_IV.xml" );
			p.fireBuildStarted( );
			p.init( );
			ProjectHelper helper = ProjectHelper.getProjectHelper( );
			helper.parse( p, initFile );
			p.executeTarget( "init" );

			monitor.subTask( "[Step "
					+ ++step[0]
					+ "] Downloading the iPortal Viewer archive file...\t[Size: "
					+ FileUtils.byteCountToDisplaySize( new File( data.getBirtViewerFile( ) ).length( ) )
					+ "] " );
			stepDetail[0] = "Download the BRDPro archive file";
			File downloadFile = getAntFile( "/templates/Download_IV.xml" );
			helper.parse( p, downloadFile );
			p.executeTarget( "download" );

			String[] subtaskName = new String[]{
				"[Step "
						+ ++step[0]
						+ "] Extracting the iPortal Viewer archive file..."
			};
			monitor.subTask( subtaskName[0] );
			stepDetail[0] = "Extracting the iPortal Viewer archive file";
			File extractFile = getAntFile( "/templates/Extract_IV.xml" );
			helper.parse( p, extractFile );

			final boolean[] flag = new boolean[]{
				false
			};

			interruptOutput( monitor, step, consoleLogger, flag, subtaskName );

			p.executeTarget( "unzip_webviewer" );

			flag[0] = true;

			Thread.sleep( 100 );

			monitor.subTask( "[Step "
					+ ++step[0]
					+ "] Replacing the iPortal Viewer workspace files..." );
			stepDetail[0] = "Replacing the iPortal Viewer workspace files";
			File reaplceFile = getAntFile( "/templates/Replace_IV.xml" );
			helper.parse( p, reaplceFile );
			p.executeTarget( "replace" );

			monitor.subTask( "[Step "
					+ ++step[0]
					+ "] Cleaning the temporary files..." );
			stepDetail[0] = "Clean the temporary files";
			File cleanFile = getAntFile( "/templates/Clean_IV.xml" );
			helper.parse( p, cleanFile );
			p.executeTarget( "clean" );

			p.fireBuildFinished( null );

			Display.getDefault( ).syncExec( new Runnable( ) {

				public void run( )
				{
					Windows.flashWindow( UIUtil.getShell( ).handle, true );
					StringBuffer buffer = new StringBuffer( );
					buffer.append( "Synchronize the iPortal Viewer workspace sucessfully." );
					MessageDialog.openInformation( null,
							"Information",
							buffer.toString( ) );
					Windows.flashWindow( UIUtil.getShell( ).handle, false );
				}
			} );

		}
		catch ( final Exception e )
		{
			if ( originRoot[0] != null )
			{
				updateClientSpecification( getTempFile( "specification.txt",
						".txt" ),
						originRoot[0] );
			}
			Display.getDefault( ).syncExec( new Runnable( ) {

				public void run( )
				{
					Windows.flashWindow( UIUtil.getShell( ).handle, true );
					LogUtil.recordErrorMsg( "Step "
							+ step[0]
							+ ": "
							+ stepDetail[0]
							+ " failed.", e, true );
					Windows.flashWindow( UIUtil.getShell( ).handle, false );
				}
			} );
		}

	}

	private void interruptOutput( final IProgressMonitor monitor,
			final int[] step, final DefaultLogger consoleLogger,
			final boolean[] flag, final String[] defaultTaskName )
	{

		Thread outputThread = new Thread( ) {

			public void run( )
			{
				try
				{
					PipedInputStream pipedIS = new PipedInputStream( );
					PipedOutputStream pipedOS = new PipedOutputStream( );
					pipedOS.connect( pipedIS );
					BufferedReader input = new BufferedReader( new InputStreamReader( pipedIS ) );
					PrintStream ps = new PrintStream( pipedOS );
					consoleLogger.setOutputPrintStream( ps );
					final String[] line = new String[1];
					String extactingStr = "[exec] Extracting";
					int length = "[exec]".length( );
					while ( ( line[0] = input.readLine( ) ) != null )
					{
						if ( !flag[0] )
						{
							int index = line[0].indexOf( extactingStr );
							if ( index != -1 )
							{
								String file = line[0].substring( index + length );
								monitor.subTask( "[Step "
										+ step[0]
										+ "]"
										+ file );
							}
							else
							{
								monitor.subTask( defaultTaskName[0] );
							}
							System.out.println( line[0] );
						}
					}
					input.close( );
					pipedIS.close( );
					consoleLogger.setOutputPrintStream( System.out );
				}
				catch ( IOException e )
				{
				}
			}
		};
		outputThread.start( );

	}

	private File getAntFile( String fileName )
	{
		File templateFile = getTempFile( fileName );
		FileUtil.writeToBinarayFile( templateFile, this.getClass( )
				.getResourceAsStream( fileName ), true );

		VelocityEngine velocityEngine = new VelocityEngine( );
		velocityEngine.setProperty( VelocityEngine.FILE_RESOURCE_LOADER_PATH,
				templateFile.getAbsoluteFile( )
						.getParentFile( )
						.getAbsolutePath( ) );
		velocityEngine.init( );

		VelocityContext context = new VelocityContext( );
		context.put( "p4Root", data.getRoot( ) );
		context.put( "p4View", data.getView( ) );
		File file = new File( data.getBirtViewerFile( ) );
		context.put( "buildPath", file.getParentFile( ).getAbsolutePath( ) );
		context.put( "buildFile", file.getName( ) );
		file = Modules.getInstance( )
				.getIPortalRepalceFile( data.getProject( ) );
		context.put( "replacePath", file.getParentFile( ).getAbsolutePath( ) );
		context.put( "replaceFile", file.getName( ) );
		context.put( "runtime", FileSystem.getCurrentDirectory( ) );

		Template template = velocityEngine.getTemplate( templateFile.getName( ) );
		StringWriter sw = new StringWriter( );
		template.merge( context, sw );

		File tempFile = getTempFile( fileName );
		FileUtil.writeToFile( tempFile, sw.toString( ).trim( ) );

		return tempFile;
	}

	private void synciPortal( final IProgressMonitor monitor, final int[] step )
	{

		final boolean[] error = new boolean[1];
		final String[] errorMessage = new String[1];
		try
		{
			final Process downloadProcess = Runtime.getRuntime( )
					.exec( new String[]{
							"cmd",
							"/c",
							"p4 -p "
									+ data.getServer( )
									+ " -u "
									+ data.getUser( )
									+ " -P "
									+ data.getPassword( )
									+ " -c "
									+ data.getClient( )
									+ " sync "
									+ ( data.isForceOperation( ) ? "-f" : "" )
									+ " //"
									+ data.getView( )
									+ "/..."
					} );

			Thread errThread = new Thread( ) {

				public void run( )
				{
					try
					{
						BufferedReader input = new BufferedReader( new InputStreamReader( downloadProcess.getErrorStream( ) ) );
						final String[] line = new String[1];
						final StringBuffer buffer = new StringBuffer( );
						while ( ( line[0] = input.readLine( ) ) != null )
						{
							buffer.append( line[0] + "\r\n" );
						}
						input.close( );

						if ( buffer.length( ) > 0 )
						{
							error[0] = true;
							errorMessage[0] = buffer.toString( );
						}
					}
					catch ( final Exception e )
					{
						Display.getDefault( ).syncExec( new Runnable( ) {

							public void run( )
							{
								Logger.getLogger( SyncIPortalWorkspace.class.getName( ) )
										.log( Level.WARNING,
												"Get error stream failed.", e ); //$NON-NLS-1$
							}
						} );
					}
				}
			};
			errThread.setDaemon( true );
			errThread.start( );

			Thread inThread = new Thread( ) {

				public void run( )
				{
					try
					{
						BufferedReader input = new BufferedReader( new InputStreamReader( downloadProcess.getInputStream( ) ) );
						final String[] line = new String[1];
						while ( ( line[0] = input.readLine( ) ) != null )
						{
							monitor.subTask( "[Step "
									+ step[0]
									+ "] Synchronizing: "
									+ line[0] );
						}
						input.close( );
					}
					catch ( final Exception e )
					{
						Display.getDefault( ).syncExec( new Runnable( ) {

							public void run( )
							{
								Logger.getLogger( SyncIPortalWorkspace.class.getName( ) )
										.log( Level.WARNING,
												"Get error stream failed.", e ); //$NON-NLS-1$
							}
						} );
					}
				}
			};
			inThread.setDaemon( true );
			inThread.start( );

			downloadProcess.waitFor( );

			Thread.sleep( 100 );

			if ( errorMessage[0] != null )
			{
				Display.getDefault( ).syncExec( new Runnable( ) {

					public void run( )
					{
						MessageDialog.openError( null, "Error", errorMessage[0] );
					}
				} );
			}

		}
		catch ( final Exception e )
		{
			Display.getDefault( ).syncExec( new Runnable( ) {

				public void run( )
				{
					MessageDialog.openError( null, "Error", e.getMessage( ) );
				}
			} );
		}

	}

	private String updateClientSpecification( final File specFile,
			final String root )
	{
		final String[] originRoot = new String[1];

		Display.getDefault( ).syncExec( new Runnable( ) {

			public void run( )
			{
				final boolean[] error = new boolean[1];
				final String[] errorMessage = new String[1];
				try
				{
					final Process downloadProcess = Runtime.getRuntime( )
							.exec( new String[]{
									"cmd",
									"/c",
									"p4 -p "
											+ data.getServer( )
											+ " -u "
											+ data.getUser( )
											+ " -P "
											+ data.getPassword( )
											+ " client -o "
											+ data.getClient( )
											+ ">"
											+ "\""
											+ specFile.getAbsolutePath( )
											+ "\""
							} );

					Thread thread = new Thread( ) {

						public void run( )
						{
							try
							{
								BufferedReader input = new BufferedReader( new InputStreamReader( downloadProcess.getErrorStream( ) ) );
								final String[] line = new String[1];
								final StringBuffer buffer = new StringBuffer( );
								while ( ( line[0] = input.readLine( ) ) != null )
								{
									buffer.append( line[0] + "\r\n" );
								}
								input.close( );

								if ( buffer.length( ) > 0 )
								{
									error[0] = true;
									errorMessage[0] = buffer.toString( );
								}
							}
							catch ( Exception e )
							{
								Logger.getLogger( SyncIPortalWorkspace.class.getName( ) )
										.log( Level.WARNING,
												"Get error stream failed.", e ); //$NON-NLS-1$
							}
						}
					};
					thread.setDaemon( true );
					thread.start( );

					downloadProcess.waitFor( );

					Thread.sleep( 100 );

					if ( errorMessage[0] != null )
					{
						MessageDialog.openError( null, "Error", errorMessage[0] );
					}
					else
					{
						if ( specFile.exists( ) )
						{
							Pattern pattern = Pattern.compile( "(?i)\n\\s*Root:\\s+\\S+" );
							Matcher matcher = pattern.matcher( FileUtil.getContent( specFile ) );
							if ( matcher.find( ) )
							{
								originRoot[0] = matcher.group( )
										.trim( )
										.split( "\\s+" )[1];
							}

							if ( originRoot[0] != null )
							{
								FileUtil.replaceFile( specFile,
										"(?i)Root:\\s+\\S+",
										"Root:	" + root );

								final Process uploadProcess = Runtime.getRuntime( )
										.exec( new String[]{
												"cmd",
												"/c",
												"p4 -p "
														+ data.getServer( )
														+ " -u "
														+ data.getUser( )
														+ " -P "
														+ data.getPassword( )
														+ " client -i "
														+ "<"
														+ "\""
														+ specFile.getAbsolutePath( )
														+ "\""
										} );

								thread = new Thread( ) {

									public void run( )
									{
										try
										{
											BufferedReader input = new BufferedReader( new InputStreamReader( uploadProcess.getErrorStream( ) ) );
											final String[] line = new String[1];
											final StringBuffer buffer = new StringBuffer( );
											while ( ( line[0] = input.readLine( ) ) != null )
											{
												buffer.append( line[0] + "\r\n" );
											}
											input.close( );

											if ( buffer.length( ) > 0 )
											{
												error[0] = true;
												errorMessage[0] = buffer.toString( );
											}
										}
										catch ( Exception e )
										{
											Logger.getLogger( SyncIPortalWorkspace.class.getName( ) )
													.log( Level.WARNING,
															"Get error stream failed.", e ); //$NON-NLS-1$
										}
									}
								};
								thread.setDaemon( true );
								thread.start( );
								uploadProcess.waitFor( );

								Thread.sleep( 100 );

								if ( errorMessage[0] != null )
								{
									MessageDialog.openError( null,
											"Error",
											errorMessage[0] );
								}
							}
							else
							{
								MessageDialog.openError( null,
										"Error",
										"Parse the client workspace specification file failed." );
							}

						}
						else
						{
							MessageDialog.openError( null,
									"Error",
									"Get the client workspace specification file failed." );
						}
					}
				}
				catch ( Exception e )
				{
					MessageDialog.openError( null, "Error", e.getMessage( ) );
				}
			}
		} );

		return originRoot[0];
	}

	private File getTempFile( String config )
	{
		return getTempFile( config, ".xml" );
	}

	private File getTempFile( String config, String suffix )
	{
		String filePath = System.getProperty( "java.io.tmpdir" )
				+ System.currentTimeMillis( )
				+ "\\"
				+ config.substring( config.lastIndexOf( '/' ) + 1,
						config.lastIndexOf( '.' ) )
				+ suffix;
		File configFile = new File( filePath );
		if ( !configFile.exists( ) )
		{
			if ( !configFile.getParentFile( ).exists( ) )
			{
				configFile.getParentFile( ).mkdirs( );
			}
		}
		return configFile;
	}

	private String checkP4ConnectionSettings( )
	{

		final boolean[] error = new boolean[1];
		final String[] errorMessage = new String[1];
		try
		{
			final Process p4Process = Runtime.getRuntime( ).exec( "p4 -p "
					+ data.getServer( )
					+ " -u "
					+ data.getUser( )
					+ " -P "
					+ data.getPassword( )
					+ " clients -u "
					+ data.getUser( ) );

			Thread thread = new Thread( ) {

				public void run( )
				{
					try
					{
						BufferedReader input = new BufferedReader( new InputStreamReader( p4Process.getErrorStream( ) ) );
						final String[] line = new String[1];
						final StringBuffer buffer = new StringBuffer( );
						while ( ( line[0] = input.readLine( ) ) != null )
						{
							buffer.append( line[0] + "\r\n" );
						}
						input.close( );

						if ( buffer.length( ) > 0 )
						{
							error[0] = true;
							errorMessage[0] = buffer.toString( );
						}
					}
					catch ( Exception e )
					{
						Logger.getLogger( SyncIPortalWorkspace.class.getName( ) )
								.log( Level.WARNING,
										"Get error stream failed.", e ); //$NON-NLS-1$
					}
				}
			};
			thread.setDaemon( true );
			thread.start( );

			StringWriter output = new StringWriter( );
			IOUtils.copy( p4Process.getInputStream( ), output );
			p4Process.waitFor( );

			Thread.sleep( 100 );

			Pattern pattern = Pattern.compile( "(?i)Client\\s+\\S+",
					Pattern.CASE_INSENSITIVE );
			Matcher matcher = pattern.matcher( output.toString( ) );

			boolean exist = false;
			while ( matcher.find( ) )
			{
				String client = matcher.group( ).replaceAll( "(?i)Client\\s+",
						"" );
				if ( client.equalsIgnoreCase( data.getClient( ).trim( ) ) )
				{
					exist = true;
					break;
				}
			}
			if ( !error[0] )
			{
				if ( !exist )
				{
					errorMessage[0] = "The client "
							+ data.getClient( ).trim( )
							+ " is unavailable.";
				}
			}
		}
		catch ( Exception e )
		{
			errorMessage[0] = e.getMessage( );
		}
		return errorMessage[0];
	}
}
