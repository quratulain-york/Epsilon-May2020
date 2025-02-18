package org.eclipse.epsilon.evl.query.tests;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.epsilon.emc.emf.SubEmfModelFactory;
import org.eclipse.epsilon.eol.compile.context.EolCompilationContext;
import org.eclipse.epsilon.eol.dom.ModelDeclaration;
import org.eclipse.epsilon.evl.EvlModule;
import org.eclipse.epsilon.evl.parse.EvlUnparser;
import org.eclipse.epsilon.evl.query.EvlRewritingHandler;
import org.eclipse.epsilon.evl.query.SubJdbcModelFactory;
import org.eclipse.epsilon.evl.staticanalyser.EvlStaticAnalyser;
import org.junit.Test;

import junit.framework.TestCase;

public class EvlRewritingTests extends TestCase {
	
	private static String removeWhitespace(String str) {
		return str.replaceAll("\\s+","");
	}

	@Test
	public static void testEmfRewriting() throws Exception {
		
		List<String> actualAndExpected = new ArrayList<>();
		actualAndExpected = prepareTestCase("testEmfRewriting.evl", "testEmfRewriting.txt",1);
		assertEquals("Failed", actualAndExpected.get(1), actualAndExpected.get(0));
	}
	
	@Test
	public static void testWithoutStatementBlock() throws Exception {
		
		List<String> actualAndExpected = new ArrayList<>();
		actualAndExpected = prepareTestCase("testWithoutStatementBlock.evl", "testWithoutStatementBlock.txt",1);
		assertEquals("Failed", actualAndExpected.get(1), actualAndExpected.get(0));
	}
	
	@Test
	public static void testMySqlExistSelect() throws Exception {
		
		List<String> actualAndExpected = new ArrayList<>();
		actualAndExpected = prepareTestCase("testMySqlExistSelect.evl", "testMySqlExistSelect.txt",1);
		assertEquals("Failed", actualAndExpected.get(1), actualAndExpected.get(0));
	}
	
//	@Test
//	public static void testNestedStatements() throws Exception {
//		
//		List<String> actualAndExpected = new ArrayList<>();
//		actualAndExpected = prepareTestCase("testNestedStatements.eol", "testNestedStatements.txt",1);
//		assertEquals("Failed", actualAndExpected.get(1), actualAndExpected.get(0));
//	}

	//	@Test
//	public static void testEugenia() throws Exception {
//		
//		List<String> actualAndExpected = new ArrayList<>();
//		actualAndExpected = prepareTestCase("testEugenia.eol", "testEugenia.dot",2);
//		assertEquals("Failed", actualAndExpected.get(1), actualAndExpected.get(0));
//	}
//	
	@Test
	public static void testMultipleEmfModels() throws Exception {
		
		List<String> actualAndExpected = new ArrayList<>();
		actualAndExpected = prepareTestCase("testMultipleEmfModels.evl", "testMultipleEmfModels.txt",1);
		assertEquals("Failed", removeWhitespace(actualAndExpected.get(1)), removeWhitespace(actualAndExpected.get(0)));
	}
//	
//	@Test
//	public static void testEmfandMySqlRewriting() throws Exception {
//		List<String> actualAndExpected = new ArrayList<>();
//		actualAndExpected = prepareTestCase("testEmfandMySqlRewriting.eol", "testEmfandMySqlRewriting.txt",1);
//		assertEquals("Failed", actualAndExpected.get(1), actualAndExpected.get(0));
//	}
	
//	@Test
//	public static void testMySqlRewriting() throws Exception {
//		List<String> actualAndExpected = new ArrayList<>();
//		actualAndExpected = prepareTestCase("testMySqlRewriting.eol", "testMySqlRewriting.txt",1);
//		assertEquals("Failed", actualAndExpected.get(1), actualAndExpected.get(0));
//	}
//	
	@Test
	public static void testOrClausev1() throws Exception {
		List<String> actualAndExpected = new ArrayList<>();
		actualAndExpected = prepareTestCase("testOrClausev1.evl", "testOrClausev1.txt",1);
		assertEquals("Failed", removeWhitespace(actualAndExpected.get(1)), removeWhitespace(actualAndExpected.get(0)));
	}
	
	@Test
	public static void testAndClausev1() throws Exception {
		List<String> actualAndExpected = new ArrayList<>();
		actualAndExpected = prepareTestCase("testAndClausev1.evl", "testAndClausev1.txt",1);
		assertEquals("Failed",removeWhitespace(actualAndExpected.get(1)), removeWhitespace(actualAndExpected.get(0)));
	}
	
	@Test
	public static void testNoIndexAnnot() throws Exception {
		List<String> actualAndExpected = new ArrayList<>();
		actualAndExpected = prepareTestCase("testNoIndexAnnot.evl", "testNoIndexAnnot.txt",1);
		assertEquals("Failed", actualAndExpected.get(1), actualAndExpected.get(0));
	}
	
	@Test
	public static void testDecomposedAST() throws Exception {
		List<String> actualAndExpected = new ArrayList<>();
		actualAndExpected = prepareTestCase("testDecomposedAST.evl", "testDecomposedAST.txt",1);
		assertEquals("Failed", actualAndExpected.get(1), actualAndExpected.get(0));
	}
	
	public static List<String> prepareTestCase(String eolFileName, String rewritedFileName, Integer option) {
		EvlModule module = new EvlModule();

		try {
			module.parse(new File("src/org/eclipse/epsilon/evl/query/tests/"+eolFileName));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        EolCompilationContext context = module.getCompilationContext();
        for (ModelDeclaration modelDeclaration : module.getDeclaredModelDeclarations()) {
			if (modelDeclaration.getDriverNameExpression().getName().equals("MySQL")) 
				context.setModelFactory(new SubJdbcModelFactory());

			if (modelDeclaration.getDriverNameExpression().getName().equals("EMF")) 
				context.setModelFactory(new SubEmfModelFactory());
		}
		
		EvlStaticAnalyser staticAnlayser = new EvlStaticAnalyser();
		staticAnlayser.validate(module);
		
		new EvlRewritingHandler().invokeRewriters(module);
		
		String actual = "";
		switch(option) {
		  case 1:
			  actual = new EvlUnparser().unparse(module);
		    break;
		  case 2:
			  String pathAndFileName = "src/org/eclipse/epsilon/evl/query/tests/generatedCallGraph.dot";
			  staticAnlayser.exportCallGraph(pathAndFileName);
			  try {
				actual = Files.readString(Path.of(pathAndFileName));
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		    break;
		  default:
		    // code block
		}
			
		
		String expected = "";
		try {
			expected = Files.readString(Path.of("src/org/eclipse/epsilon/evl/query/tests/"+rewritedFileName));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return Arrays.asList(removeWhitespace(actual),removeWhitespace(expected));
	}

}

