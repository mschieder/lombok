package lombok.core.meta;

import com.sun.tools.javac.tree.JCTree;
import lombok.core.LombokNode;
import lombok.javac.JavacNode;
import lombok.javac.handlers.JavacHandlerUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * a simple Annotation parser
 * WIP
 * @author Michael Schieder
 */
public class SimpleAnnotationParser {
	private final static Pattern PATTERN_NOPARAMS = Pattern.compile("@([A-Za-z\\._]+)");
	private final static Pattern PATTERN_PARAMS = Pattern.compile("@([A-Za-z\\._]+)\\((.+)\\)");
	
	private LombokNode<?,?,?> node;
	public SimpleAnnotationParser(LombokNode<?,?,?> node) {
		this.node = node;
	}

	public SimpleAnnotationParser() {
		super();
	}
	
	public static class Anno{
		private String name;
		private Map<String,Value> params = new LinkedHashMap<String,Value>();
		private Value singleValue;
		
		public void setName(String name) {
			this.name = name;
		}
		public String getName() {
			return name;
		}
		public Map<String, Value> getParams() {
			return params;
		}
		public void setParams(Map<String, Value> params) {
			this.params = params;
		}
		public Value getSingleValue() {
			return singleValue;
		}
		public void setSingleValue(Value singleValue) {
			this.singleValue = singleValue;
		}
		
	}
	public enum ValueType{
		LITERAL, FIELD, ARRAY;
	}
	public static class Value{
		private String literalOrField;
		private ValueType type;
		private List<Value> arrayValues = new ArrayList<Value>();;
		
		public Value(String literalOrField ) {
			this.literalOrField = literalOrField.trim();
			if(this.literalOrField.startsWith("{") && this.literalOrField.endsWith("}")) {
				type = ValueType.ARRAY;
				for (String next : this.literalOrField.substring(1, this.literalOrField.length()-1).split(",")) {
					arrayValues.add(new Value(next.trim()));
				}
			}			
			else if (this.literalOrField.contains(".") && !this.literalOrField.contains("\"")){
				type = ValueType.FIELD;
			}
			else {
				type = ValueType.LITERAL;
			}
		}

		
		public ValueType getType() {
			return type;
		}
			
		public Object getLiteral() {
			Object convertedLiteralValue = null;
			if ( type == ValueType.LITERAL) {
				if (literalOrField.startsWith("\"") && literalOrField.endsWith("\"")) {
					convertedLiteralValue = literalOrField.substring(1, literalOrField.length()-1);
				}
				else if ("true".equals(literalOrField) || "false".equals(literalOrField)) {
					convertedLiteralValue = Boolean.valueOf(literalOrField);
				}
				else {
					//default
					convertedLiteralValue = literalOrField;
				}

			}
			return convertedLiteralValue;
		}
		
		public String getField() {
			return type == ValueType.FIELD? literalOrField: null;
		}
		
		public List<Value> getArrayValues() {
			return arrayValues;
		}

		@Override
		public String toString() {
			if (type == ValueType.LITERAL){
				return getLiteral().toString();
			}
			if (type == ValueType.FIELD){
				return literalOrField;
			}
			return arrayValues.toString();
		}
	}
	
	public static Anno parseEntry(String entry, LombokNode<?,?,?> node) throws SimpleAnnotationParserException {
		SimpleAnnotationParser parser = new SimpleAnnotationParser(node);

		entry = entry.trim();
		Matcher m = PATTERN_NOPARAMS.matcher(entry);
		if (m.matches()) {
			Anno anno = new Anno();
				String name = m.group(1);
			anno.setName(name);
			return anno;
		}
		m = PATTERN_PARAMS.matcher(entry);
		if (m.matches()) {
			Anno anno = new Anno();
				String name = m.group(1);
				String paramString = m.group(2);
			anno.setName(name);
			if (!paramString.contains("=")) {
				anno.setSingleValue(parser.parseValueString(paramString));
			}
			else {
				anno.setParams(parser.parseParameterString(paramString));
				
			}
			return anno;
		}
		throw new SimpleAnnotationParserException("annotation entry not recognized: " + entry);
		
	}
	
	private List<String> splitParamString(String paramString){
		List<String> result = new ArrayList<String>();
		int cbrCount = 0;
		StringBuilder last = new StringBuilder();
		for(String nextToken: paramString.split(",")) {
			nextToken = nextToken.trim();
			if (nextToken.contains("{")){
				cbrCount++;
			}
			if (nextToken.contains("}")){
				cbrCount--;
			}
			
			if (last.length() > 0) {
				last.append(",");
			}
			if (cbrCount == 0) {
				result.add(last.append(nextToken).toString());
				last.setLength(0);
			}
			else {
				last.append(nextToken);
			}
			
		}
		if (last.length() > 0) {
			result.add(last.toString());
		}
		
		return result;
	}
	
	private Map<String,Value> parseParameterString(String paramString){
		Map<String,Value> parameterMap = new LinkedHashMap<String,Value>();
		for(String nextToken: splitParamString(paramString)) {
			if (nextToken.contains("=")) {
				String key = nextToken.substring(0, nextToken.indexOf("=")).trim();
				String value = nextToken.substring( nextToken.indexOf("=")+1).trim();
				parameterMap.put(key, parseValueString(value));
			}	
		}
		return parameterMap;
	}
	
	private Value parseValueString(String valueString, boolean replaceVariables) {
		return new Value(replaceVariables ? replaceVariables(valueString):valueString);
	}

	private Value parseValueString(String valueString) {
		return parseValueString(valueString, true);
	}

	private String replaceVariables(String valueString) {
		String returnString = valueString;
		if (returnString.contains("::name::")) {
			returnString = returnString.replace("::name::", node.getName());
		}

		String VARIABLEMARKER = "::";
		String STARTMARKER = "::@";
		int astMaxUp = 5;
		while(returnString.contains(STARTMARKER)){
			int begin = returnString.indexOf("::@");
			int end = returnString.indexOf("::",begin + STARTMARKER.length());
			String entry = returnString.substring(begin + STARTMARKER.length(), end);
			String annotationName = entry.substring(0, entry.indexOf("#"));
			String annotationSimpleName  = annotationName.substring(annotationName.lastIndexOf(".")+1);
			String paramName = entry.substring(entry.indexOf("#")+1);


			JCTree.JCAssign assign = null;
			for(int i = 0;assign == null && i <= astMaxUp;i++){
				assign = findAnnotationWithAttribute(annotationSimpleName, paramName);
				node = node.up();
			}

			if (assign != null){
				returnString = returnString.replace(returnString.substring(begin,end + 2), expressionToString(assign.getExpression()));
			}
			else{
				//TODO error not found

			}

		}

		return returnString;
	}

	private JCTree.JCAssign findAnnotationWithAttribute(String simpleAnnotationName, String attributeName){
		JCTree.JCAssign result = null;
		List<JCTree.JCAnnotation> list = JavacHandlerUtil.findAnnotations((JavacNode) node, Pattern.compile(simpleAnnotationName));
		if (!list.isEmpty()){
			for(JCTree.JCExpression next: list.get(0).getArguments()){
				if (next instanceof JCTree.JCAssign){
					String variableName = ((JCTree.JCAssign)next).getVariable().toString();
					if (attributeName.equals(variableName)){
						result = (JCTree.JCAssign)next;
						break;
					}
				}

			}
		}
		return result;
	}

	public static String expressionToString(JCTree.JCExpression expression){
		String stringValue = expression.toString();
		if (expression instanceof JCTree.JCLiteral){
			stringValue = stringValue.startsWith("\"") && stringValue.endsWith("\"")? stringValue.substring(1, stringValue.length()-1):stringValue;
		}
		return stringValue;

	}
}
