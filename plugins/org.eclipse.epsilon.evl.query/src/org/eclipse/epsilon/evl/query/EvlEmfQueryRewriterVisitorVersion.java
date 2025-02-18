package org.eclipse.epsilon.evl.query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import org.eclipse.epsilon.common.module.ModuleElement;
import org.eclipse.epsilon.eol.IEolModule;
import org.eclipse.epsilon.eol.compile.context.IEolCompilationContext;
import org.eclipse.epsilon.eol.dom.AndOperatorExpression;
import org.eclipse.epsilon.eol.dom.EqualsOperatorExpression;
import org.eclipse.epsilon.eol.dom.Expression;
import org.eclipse.epsilon.eol.dom.FeatureCallExpression;
import org.eclipse.epsilon.eol.dom.FirstOrderOperationCallExpression;
import org.eclipse.epsilon.eol.dom.GreaterThanOperatorExpression;
import org.eclipse.epsilon.eol.dom.IntegerLiteral;
import org.eclipse.epsilon.eol.dom.NameExpression;
import org.eclipse.epsilon.eol.dom.Operation;
import org.eclipse.epsilon.eol.dom.OperationCallExpression;
import org.eclipse.epsilon.eol.dom.OperatorExpression;
import org.eclipse.epsilon.eol.dom.OrOperatorExpression;
import org.eclipse.epsilon.eol.dom.Parameter;
import org.eclipse.epsilon.eol.dom.PropertyCallExpression;
import org.eclipse.epsilon.eol.dom.StatementBlock;
import org.eclipse.epsilon.eol.dom.StringLiteral;
import org.eclipse.epsilon.eol.exceptions.models.EolModelElementTypeNotFoundException;
import org.eclipse.epsilon.eol.models.IModel;
import org.eclipse.epsilon.eol.query.CreateIndexStatementsInjector;
import org.eclipse.epsilon.eol.query.DecomposedAst;
import org.eclipse.epsilon.eol.query.EolEmfRewriterVisitorVersion;
import org.eclipse.epsilon.eol.query.IndexValueGenerator;
import org.eclipse.epsilon.eol.query.ModuleElementRewriter;
import org.eclipse.epsilon.eol.types.EolModelElementType;
import org.eclipse.epsilon.erl.dom.Pre;
import org.eclipse.epsilon.evl.EvlModule;
import org.eclipse.epsilon.evl.dom.Constraint;

public class EvlEmfQueryRewriterVisitorVersion extends EolEmfRewriterVisitorVersion {
	HashSet<String> optimisableOperations;
	HashSet<String> allOperations;

	HashMap<String, HashSet<String>> potentialIndices;
	List<DecomposedAst> decomposedAsts;
	HashMap<String, Boolean> flags;

	IEolModule module;
	IModel model;
	String modelName;

	Expression rewritedQuery;
	NameExpression targetExp;
	NameExpression operationExp;
	StringLiteral modelElementName;
	StringLiteral indexField;
	Parameter param;
	
	public EvlEmfQueryRewriterVisitorVersion() {
		optimisableOperations = new HashSet<String>(Arrays.asList("select", "exists"));
		allOperations = new HashSet<String>(Arrays.asList("all", "allInstances"));
		
		potentialIndices = new HashMap<>();
		operationExp = new NameExpression("findByIndex");
		
		flags = new HashMap<>();
		flags.put("optimisableByCurrentModel" ,false);
		flags.put("logicalOperator" ,false);
	}

	@Override
	public void visit(AndOperatorExpression andOperatorExpression) {
		flags.put("logicalOperator",true);
		decomposedAsts = new ArrayList<DecomposedAst>();
		decomposedAsts = decomposedAST(andOperatorExpression);
		
		rewritedQuery = new OperationCallExpression();

		for (DecomposedAst decomposedAst : decomposedAsts) {
			ModuleElement operand = decomposedAst.getModuleElement();
			if(operand instanceof OperatorExpression) {
			visit((OperatorExpression) operand, decomposedAst.getOperator()); 
			if (!flags.get("logicalOperator"))
				return;
			}
		}
	}

	@Override
	public void visit(EqualsOperatorExpression equalsOperatorExpression) {
		if (flags.get("optimisableByCurrentModel")) {
			Expression firstOperand = equalsOperatorExpression.getFirstOperand();
			if (firstOperand != null && firstOperand instanceof PropertyCallExpression)
				visit((PropertyCallExpression) firstOperand, true);

			ModuleElement indexValueExpression = equalsOperatorExpression.getSecondOperand();
			Expression indexValue = new IndexValueGenerator(indexValueExpression).generateIndexValue();


			rewritedQuery = new OperationCallExpression(targetExp, operationExp, modelElementName, indexField,
					indexValue);
				potentialIndices.get(modelElementName.getValue()).add(indexField.getValue());

		} else {
			if (equalsOperatorExpression.getFirstOperand() != null)
				equalsOperatorExpression.getFirstOperand().accept(this);
			if (equalsOperatorExpression.getSecondOperand() != null)
				equalsOperatorExpression.getSecondOperand().accept(this);
		}
	}

	@Override
	public void visit(FirstOrderOperationCallExpression firstOrderOperationCallExpression) {
		flags.put("logicalOperator",false);
		rewritedQuery = new OperationCallExpression();
		if (optimisableOperations.contains(firstOrderOperationCallExpression.getName())) {
			if (firstOrderOperationCallExpression.getTargetExpression() instanceof PropertyCallExpression) {
				PropertyCallExpression target = (PropertyCallExpression) firstOrderOperationCallExpression
						.getTargetExpression();
				visit(target, false);
			}
			if (flags.get("optimisableByCurrentModel")) {
				Iterator<Parameter> pi = firstOrderOperationCallExpression.getParameters().iterator();
				while (pi.hasNext()) {
					pi.next().accept(this);
				}

				if (!firstOrderOperationCallExpression.getExpressions().isEmpty()) {
					Iterator<Expression> ei = firstOrderOperationCallExpression.getExpressions().iterator();
					while (ei.hasNext()) {
						param = firstOrderOperationCallExpression.getParameters().get(0);
						ei.next().accept(this);
					}
				}
				if(firstOrderOperationCallExpression.getName().equals("exists")) {
					IntegerLiteral i = new IntegerLiteral(0);
					i.setText("0");
					rewritedQuery = new GreaterThanOperatorExpression(new OperationCallExpression(rewritedQuery, new NameExpression("size")),i);
				}
				if ((flags.get("optimisableByCurrentModel")|| flags.get("logicalOperator"))) {
					new ModuleElementRewriter(firstOrderOperationCallExpression, rewritedQuery).rewrite();
				}
				flags.put("optimisableByCurrentModel",false);
			} else {
				flags.put("optimisableByCurrentModel",false);
				firstOrderOperationCallExpression.getTargetExpression().accept(this);
				Iterator<Parameter> pi = firstOrderOperationCallExpression.getParameters().iterator();
				while (pi.hasNext()) {
					pi.next().accept(this);
				}

				if (!firstOrderOperationCallExpression.getExpressions().isEmpty()) {
					Iterator<Expression> ei = firstOrderOperationCallExpression.getExpressions().iterator();
					while (ei.hasNext()) {
						param = firstOrderOperationCallExpression.getParameters().get(0);
						ei.next().accept(this);
					}
				}

			}
		}

	}

	@Override
	public void visit(OperationCallExpression operationCallExpression) {
//		operationCallExpression.getTargetExpression().accept(this);
		String operationName = operationCallExpression.getName();

		if (allOperations.contains(operationName)) {

			EolModelElementType modelElement = null;
			if (operationCallExpression.getTargetExpression().getResolvedType() instanceof EolModelElementType)
				modelElement = (EolModelElementType) operationCallExpression.getTargetExpression().getResolvedType();

			try {
				if (modelElement.getModel(module.getCompilationContext()) == model) {
					flags.put("optimisableByCurrentModel" ,true);
					modelName = modelElement.getModelName();
					model.setName(modelName);
					targetExp = new NameExpression(modelName);
					operationExp = new NameExpression("findByIndex");
					modelElementName = new StringLiteral(modelElement.getTypeName());

					if (potentialIndices.get(modelElementName.getValue()) == null) {
						potentialIndices.put(modelElementName.getValue(), new HashSet<String>());
					}
				} else
					flags.put("optimisableByCurrentModel" ,false);

			} catch (EolModelElementTypeNotFoundException e) {
				e.printStackTrace();
			}
		}
		if (operationCallExpression.getTargetExpression() != null)
			operationCallExpression.getTargetExpression().accept(this);
		if ((!operationCallExpression.getParameterExpressions().isEmpty())) {
			Iterator<Expression> pi = operationCallExpression.getParameterExpressions().iterator();
			while (pi.hasNext()) {
				pi.next().accept(this);
			}
		}
	}

	@Override
	public void visit(OrOperatorExpression orOperatorExpression) {
		if (flags.get("optimisableByCurrentModel")) {
			flags.put("logicalOperator", true);
			decomposedAsts = new ArrayList<DecomposedAst>();
			decomposedAsts = decomposedAST(orOperatorExpression);

			rewritedQuery = new OperationCallExpression();

			for (DecomposedAst decomposedAst : decomposedAsts) {
				ModuleElement operand = decomposedAst.getModuleElement();
				if(operand instanceof OperatorExpression) {
				visit((OperatorExpression) operand, decomposedAst.getOperator()); 
				if (!flags.get("logicalOperator"))
					return;
				}
			}
		} else {
			if (orOperatorExpression.getFirstOperand() != null)
				orOperatorExpression.getFirstOperand().accept(this);
			if (orOperatorExpression.getSecondOperand() != null)
				orOperatorExpression.getSecondOperand().accept(this);
		}

	}

	public void visit(OperatorExpression operand, String logicalOperatorName) {
		if (flags.get("optimisableByCurrentModel")) {
			if (logicalOperatorName.equals("or")) {
				if (operand instanceof EqualsOperatorExpression) {
					operand = (EqualsOperatorExpression) operand;
					Expression firstOperand = ((EqualsOperatorExpression) operand).getFirstOperand();
					if (firstOperand != null && firstOperand instanceof PropertyCallExpression)
						visit((PropertyCallExpression) firstOperand, true);
					ModuleElement indexValueExpression = ((EqualsOperatorExpression) operand).getSecondOperand();
					Expression indexValue = new IndexValueGenerator(indexValueExpression).generateIndexValue();

					if (((FeatureCallExpression) rewritedQuery).getName() == null)
						rewritedQuery = new OperationCallExpression(targetExp, operationExp, modelElementName,
								indexField, indexValue);

					else {
						rewritedQuery = new OperationCallExpression(rewritedQuery, new NameExpression("includingAll"),
								new OperationCallExpression(targetExp, operationExp, modelElementName, indexField,
										indexValue));
					}
						potentialIndices.get(modelElementName.getValue()).add(indexField.getValue());

				} else {
					rewritedQuery = new FirstOrderOperationCallExpression(rewritedQuery, new NameExpression("select"),
							param, (Expression) operand);

				}
			}
			if (logicalOperatorName.equals("and")) {
				boolean flag = false;
				if (operand instanceof EqualsOperatorExpression) {
					operand = (EqualsOperatorExpression) operand;
					Expression firstOperand = ((EqualsOperatorExpression) operand).getFirstOperand();
					if (firstOperand != null && (firstOperand instanceof FeatureCallExpression)) {
						visit((PropertyCallExpression) firstOperand, true);
					ModuleElement indexValueExpression = ((EqualsOperatorExpression) operand).getSecondOperand();
					Expression indexValue = new IndexValueGenerator(indexValueExpression).generateIndexValue();

					if (((FeatureCallExpression) rewritedQuery).getName() == null)
						rewritedQuery = new OperationCallExpression(targetExp, operationExp, modelElementName,
								indexField, indexValue);
					else {
						if (!(((FeatureCallExpression) firstOperand).getTargetExpression() instanceof NameExpression)) {
							rewritedQuery = new FirstOrderOperationCallExpression(rewritedQuery,
									new NameExpression("select"), param,
									new EqualsOperatorExpression(new PropertyCallExpression(
											((FeatureCallExpression) firstOperand).getTargetExpression(),
											new NameExpression(indexField.getValue())), indexValue));

						} else
							rewritedQuery = new FirstOrderOperationCallExpression(rewritedQuery,
									new NameExpression("select"), param,
									new EqualsOperatorExpression(new PropertyCallExpression(param.getNameExpression(),
											new NameExpression(indexField.getValue())), indexValue));
						flag = true;
					}
					if (!flag) {
						potentialIndices.get(modelElementName.getValue()).add(indexField.getValue());
					}
				} else {
					rewritedQuery = new FirstOrderOperationCallExpression(rewritedQuery, new NameExpression("select"),
							param, (Expression) operand);

				}
				}
			}
		}
	}

	public void visit(PropertyCallExpression propertyCallExpression, boolean flag) {
		if (flag)
			indexField = new StringLiteral(propertyCallExpression.getName());
		else {
			propertyCallExpression.getTargetExpression().accept(this);
			String operationName = propertyCallExpression.getName();

			if (allOperations.contains(operationName)) {

				EolModelElementType modelElement = null;
				if (propertyCallExpression.getTargetExpression().getResolvedType() instanceof EolModelElementType)
					modelElement = (EolModelElementType) propertyCallExpression.getTargetExpression().getResolvedType();

				try {
					if (modelElement.getModel(module.getCompilationContext()) == model) {
						flags.put("optimisableByCurrentModel", true);
						modelName = modelElement.getModelName();
						model.setName(modelName);
						targetExp = new NameExpression(modelName);
						modelElementName = new StringLiteral(modelElement.getTypeName());

						if (potentialIndices.get(modelElementName.getValue()) == null) {
							potentialIndices.put(modelElementName.getValue(), new HashSet<String>());
						}
					} else
						flags.put("optimisableByCurrentModel", false);

				} catch (EolModelElementTypeNotFoundException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	public void rewrite(IModel model, IEolModule module, IEolCompilationContext context) {
		EvlModule evlModule = (EvlModule) module;
		this.model = model;
		this.module = evlModule;
		StatementBlock statements;
		optimisableOperations = new HashSet<String>(Arrays.asList("select","exists"));
		allOperations = new HashSet<String>(Arrays.asList("all", "allInstances"));
		
		for (Constraint constraint : evlModule.getConstraints()) {
			if (constraint.getAnnotation("noindex") == null) {
				if (constraint.getCheckBlock().getBody() instanceof StatementBlock) {
					statements = (StatementBlock) constraint.getCheckBlock().getBody();
					statements.accept(this);
				} else {
					((Expression) constraint.getCheckBlock().getBody()).accept(this);
				}
			}
		}
		for (Operation operation : module.getDeclaredOperations()) {
			operation.accept(this);
		}
		
		for(Constraint constraint : evlModule.getConstraints()) {
			if (constraint.getAnnotation("noindex") == null) {
				if (constraint.getCheckBlock().getBody() instanceof StatementBlock) {
					statements = (StatementBlock) constraint.getCheckBlock().getBody();
					statements.accept(this);
				} else {
					((Expression) constraint.getCheckBlock().getBody()).accept(this);
				}
			}
		}
		if(evlModule.getDeclaredPre().size()==0) {
			Pre p = new Pre();
			p.setBody(new StatementBlock());
			evlModule.getDeclaredPre().add(p);
		}
		new CreateIndexStatementsInjector().inject(evlModule, modelName, potentialIndices);

	}

	public List<DecomposedAst> decomposedAST(Expression ast) {
		List<DecomposedAst> decomposed = new ArrayList<>();
		Expression firstOperand = ((OperatorExpression) ast).getFirstOperand();

		if (firstOperand instanceof OrOperatorExpression) {
			decomposedAsts.add(new DecomposedAst(((OperatorExpression) ast).getSecondOperand(), ((OperatorExpression)ast).getOperator()));
			return decomposedAST(firstOperand);
		}
		if (firstOperand instanceof AndOperatorExpression) {
			decomposedAsts.add(new DecomposedAst(((OperatorExpression) ast).getSecondOperand(), ((OperatorExpression)ast).getOperator()));
			return decomposedAST(firstOperand);
		}
		else {
			for(ModuleElement me : ast.getChildren())
				decomposed.add(new DecomposedAst(me, ((OperatorExpression)ast).getOperator()));
			Collections.reverse(decomposed);
			decomposedAsts.addAll(decomposed);
		}
		Collections.reverse(decomposedAsts);
		return decomposedAsts;

	}

}
