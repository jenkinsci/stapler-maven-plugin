/*
 * Copyright (c) 2004-2010, Kohsuke Kawaguchi
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided
 * that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright notice, this list of
 *       conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright notice, this list of
 *       conditions and the following disclaimer in the documentation and/or other materials
 *       provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY
 * AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER
 * IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF
 * THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.kohsuke.stapler;

import org.codehaus.plexus.compiler.CompilerConfiguration;
import org.codehaus.plexus.compiler.CompilerException;
import org.codehaus.plexus.compiler.CompilerError;
import org.codehaus.plexus.compiler.javac.JavacCompiler;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.CommandLineException;

import java.io.File;
import java.io.PrintWriter;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.StringReader;
import java.io.FileWriter;
import java.util.Collections;
import java.util.List;
import java.net.URL;
import java.net.MalformedURLException;

/**
 * {@link Compiler} for APT.
 *
 * <p>
 * In Maven, {@link Compiler} handles the actual compiler invocation.
 *
 * @author Kohsuke Kawaguchi
 */
public class AptCompiler extends JavacCompiler {

    public List compile( CompilerConfiguration config ) throws CompilerException {
        // force 1.5
        config.setTargetVersion("1.5");
        config.setSourceVersion("1.5");


        File destinationDir = new File( config.getOutputLocation() );

        if ( !destinationDir.exists() )
        {
            destinationDir.mkdirs();
        }

        String[] sourceFiles = getSourceFiles( config );

        if ( sourceFiles.length == 0 )
        {
            return Collections.EMPTY_LIST;
        }

        getLogger().info( "Compiling " + sourceFiles.length + " " +
                          "source file" + ( sourceFiles.length == 1 ? "" : "s" ) +
                          " to " + destinationDir.getAbsolutePath() );

        if (config.isFork()) {
            // forking a compiler requires classpath set up and passing AnnotationProcessorFactory.
            config.addClasspathEntry(whichJar(AnnotationProcessorFactoryImpl.class));
            config.addCompilerCustomArgument("-factory",AnnotationProcessorFactoryImpl.class.getName());
        }

        // this is where the META-INF/services get generated.
        config.addCompilerCustomArgument("-s",new File(config.getOutputLocation()).getAbsolutePath());
        String[] args = buildCompilerArguments( config, sourceFiles );

        if (config.isFork()) {
            String executable = config.getExecutable();
            if (StringUtils.isEmpty(executable)) {
                File apt = new File(new File(System.getProperty("java.home")),"bin/apt"); // Mac puts $JAVA_HOME to JDK
                if (!apt.exists())
                    // on other platforms $JAVA_HOME is JRE in JDK.
                    apt = new File(new File(System.getProperty("java.home")),"../bin/apt");
                executable = apt.getAbsolutePath();
            }
            return compileOutOfProcess(config, executable, args);
        } else {
            return compileInProcess(args);
        }
    }

    private String whichJar(Class c) throws CompilerException {
        try {
            String url = c.getClassLoader().getResource(c.getName().replace('.', '/') + ".class").toExternalForm();
            if (url.startsWith("jar:")) {
                url = url.substring(4);
                url = url.substring(0,url.indexOf('!'));
                return new URL(url).getPath();
            }
            throw new CompilerException("Failed to infer classpath for "+c);
        } catch (MalformedURLException e) {
            throw new CompilerException("Failed to infer classpath for "+c,e);
        }
    }

    /**
     * Compile the java sources in the current JVM, without calling an external executable,
     * using <code>com.sun.tools.javac.Main</code> class
     *
     * @param args arguments for the compiler as they would be used in the command line javac
     * @return List of CompilerError objects with the errors encountered.
     * @throws CompilerException
     */
    protected List compileInProcess( String[] args ) throws CompilerException {
        com.sun.tools.apt.Main aptTool = new com.sun.tools.apt.Main();
        int r = aptTool.process(new AnnotationProcessorFactoryImpl(),
            new PrintWriter(System.out,true),args);
        if(r!=0)
            throw new CompilerException("APT failed: "+r);

        // TODO: should I try to parse the output?
        return Collections.emptyList();
    }

    protected List compileOutOfProcess(CompilerConfiguration config, String executable, String[] args)
            throws CompilerException {
        Commandline cli = new Commandline();

        cli.setWorkingDirectory(config.getWorkingDirectory().getAbsolutePath());

        cli.setExecutable(executable);

        try {
            File argumentsFile = createFileWithArguments(args);
            cli.addArguments(new String[]{"@" + argumentsFile.getCanonicalPath().replace(File.separatorChar, '/')});

            if (!StringUtils.isEmpty(config.getMaxmem())) {
                cli.addArguments(new String[]{"-J-Xmx" + config.getMaxmem()});
            }

            if (!StringUtils.isEmpty(config.getMeminitial())) {
                cli.addArguments(new String[]{"-J-Xms" + config.getMeminitial()});
            }
        }
        catch (IOException e) {
            throw new CompilerException("Error creating file with javac arguments", e);
        }

        CommandLineUtils.StringStreamConsumer out = new CommandLineUtils.StringStreamConsumer();

        CommandLineUtils.StringStreamConsumer err = new CommandLineUtils.StringStreamConsumer();

        int returnCode;

        List<CompilerError> messages;

        try {
            returnCode = CommandLineUtils.executeCommandLine(cli, out, err);

            messages = parseModernStream(new BufferedReader(new StringReader(err.getOutput())));
        }
        catch (CommandLineException e) {
            throw new CompilerException("Error while executing the external compiler.", e);
        }
        catch (IOException e) {
            throw new CompilerException("Error while executing the external compiler.", e);
        }

        if (returnCode != 0 && messages.isEmpty()) {
            if (err.getOutput().length() == 0) {
                throw new CompilerException("Unknown error trying to execute the external compiler: " + EOL
                        + cli.toString());
            } else {
                messages.add(new CompilerError("Failure executing javac,  but could not parse the error:" + EOL
                        + err.getOutput(), true));
            }
        }

        return messages;
    }

    private File createFileWithArguments(String[] args) throws IOException {
        PrintWriter writer = null;
        try {
            File tempFile = File.createTempFile(JavacCompiler.class.getName(), "arguments");
            tempFile.deleteOnExit();

            writer = new PrintWriter(new FileWriter(tempFile));

            for (int i = 0; i < args.length; i++) {
                String argValue = args[i].replace(File.separatorChar, '/');

                writer.write("\"" + argValue + "\"");

                writer.write(EOL);
            }

            writer.flush();

            return tempFile;

        }
        finally {
            if (writer != null) {
                writer.close();
            }
        }
    }
}
