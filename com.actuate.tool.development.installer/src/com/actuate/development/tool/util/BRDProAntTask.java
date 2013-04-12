
package com.actuate.development.tool.util;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.ProjectHelper;
import org.sf.feeling.swt.win32.extension.io.FileSystem;

public class BRDProAntTask
{

	public BRDProAntTask( String file, String target )
	{
		try
		{
			Project p = new Project( );
			p.setBasedir( FileSystem.getCurrentDirectory( ) );

			ProjectHelper helper = ProjectHelper.getProjectHelper( );
			p.fireBuildStarted( );
			p.init( );
			helper.parse( p, new File( file ) );

			p.executeTarget( target );
		}
		catch ( BuildException e )
		{
			StringWriter writer = new StringWriter( );
			PrintWriter ps = new PrintWriter( writer );
			e.printStackTrace( ps );
			try
			{
				writer.close( );
			}
			catch ( IOException e1 )
			{
			}
			System.err.println( writer.toString( ) );
			System.exit( -1 );
		}
	}

	public static void main( String[] args )
	{
		if ( args.length != 2 )
			return;
		ClassPathUpdater.loadClasspath( );
		new BRDProAntTask( args[0], args[1] );
	}
}
