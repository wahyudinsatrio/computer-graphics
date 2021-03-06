/*
 * Copyright (c) 2002-2008 LWJGL Project
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * * Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the distribution.
 *
 * * Neither the name of 'LWJGL' nor the names of
 *   its contributors may be used to endorse or promote products derived
 *   from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.lwjgl.util.generator.opengl;

import org.lwjgl.util.generator.*;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Iterator;

import com.sun.mirror.declaration.InterfaceDeclaration;
import com.sun.mirror.declaration.MethodDeclaration;
import com.sun.mirror.type.InterfaceType;

/**
 * Generator visitor for the context capabilities generator tool
 *
 * @author elias_naur <elias_naur@users.sourceforge.net>
 * @version $Revision: 3334 $
 *          $Id: ContextCapabilitiesGenerator.java 3334 2010-04-22 23:21:48Z spasi $
 */
public class GLESCapabilitiesGenerator {

	private static final String STUBS_LOADED_NAME = "loaded_stubs";
	private static final String ALL_INIT_METHOD_NAME = "initAllStubs";
	private static final String POINTER_INITIALIZER_POSTFIX = "_initNativeFunctionAddresses";
	private static final String CACHED_EXTS_VAR_NAME = "supported_extensions";
	private static final String EXTENSION_PREFIX = "GL_";
	private static final String CORE_PREFIX = "Open";

	public static void generateClassPrologue(PrintWriter writer, boolean context_specific, boolean generate_error_checks) {
		writer.println("public class " + Utils.CONTEXT_CAPS_CLASS_NAME + " {");
		writer.println("\tstatic final boolean DEBUG = " + Boolean.toString(generate_error_checks) + ";");
		writer.println();
		if ( !context_specific ) {
			writer.println("\tprivate static boolean " + STUBS_LOADED_NAME + ";");
		}
	}

	public static void generateInitializerPrologue(PrintWriter writer) {
		writer.println("\t" + Utils.CONTEXT_CAPS_CLASS_NAME + "() throws LWJGLException {");
		writer.println("\t\tSet<String> " + CACHED_EXTS_VAR_NAME + " = " + ALL_INIT_METHOD_NAME + "();");
	}

	private static String translateFieldName(String interface_name) {
		if ( interface_name.startsWith("GL") )
			return CORE_PREFIX + interface_name;
		else
			return EXTENSION_PREFIX + interface_name;
	}

	public static void generateSuperClassAdds(PrintWriter writer, InterfaceDeclaration d) {
		Collection<InterfaceType> super_interfaces = d.getSuperinterfaces();
		if ( super_interfaces.size() > 1 )
			throw new RuntimeException(d + " extends more than one other interface");
		if ( super_interfaces.size() == 1 ) {
			InterfaceType super_interface = super_interfaces.iterator().next();
			writer.print("\t\tif (" + CACHED_EXTS_VAR_NAME + ".contains(\"");
			writer.println(translateFieldName(d.getSimpleName()) + "\"))");
			writer.print("\t\t\t");
			generateAddExtension(writer, super_interface.getDeclaration());
		}
	}

	public static void generateInitializer(PrintWriter writer, InterfaceDeclaration d) {
		String translated_field_name = translateFieldName(d.getSimpleName());
		writer.print("\t\tthis." + translated_field_name + " = ");
		writer.print(CACHED_EXTS_VAR_NAME + ".contains(\"");
		writer.print(translated_field_name + "\")");
		Collection<InterfaceType> super_interfaces = d.getSuperinterfaces();
		if ( super_interfaces.size() > 1 )
			throw new RuntimeException(d + " extends more than one other interface");
		if ( super_interfaces.size() == 1 ) {
			InterfaceType super_interface = super_interfaces.iterator().next();
			writer.println();
			writer.print("\t\t\t&& " + CACHED_EXTS_VAR_NAME + ".contains(\"");
			writer.print(translateFieldName(super_interface.getDeclaration().getSimpleName()) + "\")");
		}
		Alias alias_annotation = d.getAnnotation(Alias.class);
		if ( alias_annotation != null ) {
			writer.println();
			writer.print("\t\t\t|| " + CACHED_EXTS_VAR_NAME + ".contains(\"");
			writer.print(translateFieldName(alias_annotation.value()) + "\")");
		}
		writer.println(";");
	}

	private static String getAddressesInitializerName(String class_name) {
		return class_name + POINTER_INITIALIZER_POSTFIX;
	}

	public static void generateInitStubsPrologue(PrintWriter writer, boolean context_specific) {
		writer.println("\tprivate Set<String> " + ALL_INIT_METHOD_NAME + "() throws LWJGLException {");

		if ( context_specific ) {
            // Load the basic pointers we need to detect OpenGL version and supported extensions.
            writer.println("\t\tglGetError = GLContext.getFunctionAddress(\"glGetError\");");
            writer.println("\t\tglGetString = GLContext.getFunctionAddress(\"glGetString\");");
		}

		// Get the supported extensions set.
		writer.println("\t\tGLContext.setCapabilities(this);");
		writer.println("\t\tSet<String> " + CACHED_EXTS_VAR_NAME + " = new HashSet<String>(256);");
		if ( !context_specific )
			writer.println("\t\tGLContext.doInitNativeStubs(GLES20.class);");
		writer.println("\t\tGLContext.getSupportedExtensions(" + CACHED_EXTS_VAR_NAME + ");");

		if ( !context_specific ) {
			writer.println("\t\tif (" + STUBS_LOADED_NAME + ")");
			writer.println("\t\t\treturn " + CACHED_EXTS_VAR_NAME + ";");
		} else {
			writer.println("\t\tif (!" + getAddressesInitializerName("GLES20") + "())");
			writer.println("\t\t\tthrow new LWJGLException(\"GL ES 2.0 not supported\");");
		}
	}

	public static void generateInitStubsEpilogue(PrintWriter writer, boolean context_specific) {
		if ( !context_specific )
			writer.println("\t\t" + STUBS_LOADED_NAME + " = true;");
		writer.println("\t\treturn " + CACHED_EXTS_VAR_NAME + ";");
		writer.println("\t}");
	}

	public static void generateUnloadStubs(PrintWriter writer, InterfaceDeclaration d) {
		// TODO: Remove GLES
		if ( d.getMethods().size() > 0 && !d.getSimpleName().startsWith("GLES") ) {
			writer.print("\t\tGLContext.resetNativeStubs(" + Utils.getSimpleClassName(d));
			writer.println(".class);");
		}
	}

	public static void generateInitStubs(PrintWriter writer, InterfaceDeclaration d, boolean context_specific) {
		if ( d.getMethods().size() > 0 ) {
			if ( context_specific ) {
				final Alias alias_annotation = d.getAnnotation(Alias.class);

				if ( d.getAnnotation(ForceInit.class) != null )
					writer.println("\t\t" + CACHED_EXTS_VAR_NAME + ".add(\"" + translateFieldName(d.getSimpleName()) + "\");");
				writer.print("\t\tif (");
				if ( alias_annotation != null )
					writer.print("(");
				writer.print(CACHED_EXTS_VAR_NAME + ".contains(\"");
				writer.print(translateFieldName(d.getSimpleName()) + "\")");
				if ( alias_annotation != null ) {
					writer.print(" || " + CACHED_EXTS_VAR_NAME + ".contains(\"");
					writer.print(translateFieldName(alias_annotation.value()) + "\"))");
				}
				writer.print(" && !" + getAddressesInitializerName(d.getSimpleName()) + "(");
				if ( d.getAnnotation(Dependent.class) != null )
					writer.print("supported_extensions");
				if ( alias_annotation != null ) {
					writer.println(")) {");
					writer.print("\t\t\tremove(" + CACHED_EXTS_VAR_NAME + ", \"");
					writer.println(translateFieldName(alias_annotation.value()) + "\");");
				} else
					writer.println("))");
				writer.print("\t\t\tremove(" + CACHED_EXTS_VAR_NAME + ", \"");
				writer.println(translateFieldName(d.getSimpleName()) + "\");");
				if ( alias_annotation != null )
					writer.println("\t\t}");
			} else {
				writer.print("\t\tGLContext." + Utils.STUB_INITIALIZER_NAME + "(" + Utils.getSimpleClassName(d));
				writer.println(".class, " + CACHED_EXTS_VAR_NAME + ", \"" + translateFieldName(d.getSimpleName()) + "\");");
			}
		}
	}

	private static void generateAddExtension(PrintWriter writer, InterfaceDeclaration d) {
		writer.print(CACHED_EXTS_VAR_NAME + ".add(\"");
		writer.println(translateFieldName(d.getSimpleName()) + "\");");
	}

	public static void generateAddressesInitializers(PrintWriter writer, InterfaceDeclaration d) {
		Iterator<? extends MethodDeclaration> methods = d.getMethods().iterator();
		if ( !methods.hasNext() )
			return;

		writer.print("\tprivate boolean " + getAddressesInitializerName(d.getSimpleName()) + "(");

		boolean optional;
		Dependent dependent = d.getAnnotation(Dependent.class);
		if ( dependent != null ) {
			writer.print("Set<String> supported_extensions");
		}

		Alias alias_annotation = d.getAnnotation(Alias.class);
		boolean aliased = alias_annotation != null && alias_annotation.postfix().length() > 0;

		writer.println(") {");
		writer.println("\t\treturn ");

		boolean first = true;
		while ( methods.hasNext() ) {
			MethodDeclaration method = methods.next();
			if ( method.getAnnotation(Alternate.class) != null )
				continue;

			if ( !first )
				writer.println(" &");
			else
				first = false;

			optional = method.getAnnotation(Optional.class) != null;
			dependent = method.getAnnotation(Dependent.class);

			writer.print("\t\t\t(");
			if ( optional )
				writer.print('(');
			if ( dependent != null ) {
				if ( dependent.value().indexOf(',') == -1 )
					writer.print("!supported_extensions.contains(\"" + dependent.value() + "\") || ");
				else {
					writer.print("!(false");
					for ( String extension : dependent.value().split(",") )
						writer.print(" || supported_extensions.contains(\"" + extension + "\")");
					writer.print(") || ");
				}
			}
			if ( dependent != null )
				writer.print('(');
			writer.print(Utils.getFunctionAddressName(d, method) + " = ");
			PlatformDependent platform_dependent = method.getAnnotation(PlatformDependent.class);
			if ( platform_dependent != null ) {
				EnumSet<Platform> platform_set = EnumSet.copyOf(Arrays.asList(platform_dependent.value()));
				writer.print("GLContext.getPlatformSpecificFunctionAddress(\"");
				writer.print(Platform.ALL.getPrefix() + "\", ");
				writer.print("new String[]{");
				Iterator<Platform> platforms = platform_set.iterator();
				while ( platforms.hasNext() ) {
					writer.print("\"" + platforms.next().getOSPrefix() + "\"");
					if ( platforms.hasNext() )
						writer.print(", ");
				}
				writer.print("}, new String[]{");
				platforms = platform_set.iterator();
				while ( platforms.hasNext() ) {
					writer.print("\"" + platforms.next().getPrefix() + "\"");
					if ( platforms.hasNext() )
						writer.print(", ");
				}
				writer.print("}, ");
			} else if ( aliased ) {
				writer.print("GLContext.getFunctionAddress(new String[] {\"" + method.getSimpleName() + "\",\"" + method.getSimpleName() + alias_annotation.postfix() + "\"})) != 0");
			} else
				writer.print("GLContext.getFunctionAddress(");
			if ( !aliased )
				writer.print("\"" + method.getSimpleName() + "\")) != 0");
			if ( dependent != null )
				writer.print(')');
			if ( optional )
				writer.print(" || true)");
		}
		writer.println(";");
		writer.println("\t}");
		writer.println();
	}

	public static void generateSymbolAddresses(PrintWriter writer, InterfaceDeclaration d) {
		boolean first = true;
		for ( final MethodDeclaration method : d.getMethods() ) {
			if ( method.getAnnotation(Alternate.class) != null || method.getAnnotation(Reuse.class) != null )
				continue;

			if ( first ) {
				writer.println("\t// " + d.getSimpleName());
				first = false;
			}
			writer.println("\tint " + Utils.getFunctionAddressName(d, method) + ";");
		}
	}

	public static void generateField(PrintWriter writer, InterfaceDeclaration d) {
		writer.println("\tpublic final boolean " + translateFieldName(d.getSimpleName()) + ";");
	}

}