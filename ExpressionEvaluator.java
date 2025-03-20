import java.util.concurrent.*;
import java.util.*;

public class ExpressionEvaluator {
    // Número ideal de threads baseado nos processadores disponíveis
    private final int OPTIMAL_THREADS = Runtime.getRuntime().availableProcessors();
    // Pool de threads para execução paralela
    private ExecutorService threadPool;
    // Objeto de bloqueio para sincronização
    private final Object lock = new Object();
    // Conta as tarefas pendentes
    private int pendingTasks = 0;
    // Armazena os valores das variáveis
    private Map<String, Double> variableValues = new HashMap<>();

    // Inicia o pool de threads
    public ExpressionEvaluator() {
        threadPool = Executors.newFixedThreadPool(OPTIMAL_THREADS);
    }

    // Define o valor de uma variável
    public void setVariable(String name, double value) {
        variableValues.put(name, value);
    }

    // Avalia uma expressão e retorna o resultado
    public double evaluate(String expression) throws InterruptedException, ExecutionException {
        Node root = parse(expression);
        return evaluateNode(root).get();
    }

    // Avalia um nó da árvore de expressão de forma recursiva
    private Future<Double> evaluateNode(Node node) {
        // Nó de valor constante
        if (node instanceof ValueNode) {
            return CompletableFuture.completedFuture(((ValueNode) node).getValue());
        } 
        // Nó de variável
        else if (node instanceof VariableNode) {
            String varName = ((VariableNode) node).getName();
            if (!variableValues.containsKey(varName)) {
                throw new RuntimeException("Variável não definida: " + varName);
            }
            return CompletableFuture.completedFuture(variableValues.get(varName));
        } 
        // Nó de operador (cálculo paralelo)
        else {
            OperatorNode opNode = (OperatorNode) node;
            
            // Incrementa o contador de tarefas pendentes (sincronizado)
            synchronized (lock) {
                pendingTasks++;
            }
            
            // Submete a tarefa para execução paralela
            return threadPool.submit(() -> {
                try {
                    // Avalia os operandos esquerdo e direito
                    double leftValue = evaluateNode(opNode.getLeft()).get();
                    double rightValue = evaluateNode(opNode.getRight()).get();
                    // Aplica o operador aos valores
                    double result = applyOperator(opNode.getOperator(), leftValue, rightValue);
                    
                    // Decrementa o contador de tarefas e notifica se todas concluíram
                    synchronized (lock) {
                        pendingTasks--;
                        if (pendingTasks == 0) {
                            lock.notifyAll();
                        }
                    }
                    
                    return result;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    // Aplica o operador aos operandos
    private double applyOperator(char operator, double left, double right) {
        switch (operator) {
            case '+': return left + right;
            case '-': return left - right;
            case '*': return left * right;
            case '/': return left / right;
            default: throw new IllegalArgumentException("Operador desconhecido: " + operator);
        }
    }

    // Analisa a expressão e constrói a árvore sintática
    private Node parse(String expression) {
        expression = expression.replaceAll("\\s+", "");
        return parseExpression(expression);
    }

    // Analisa recursivamente a expressão
    private Node parseExpression(String expr) {
        if (expr.isEmpty()) return new ValueNode(0);
        
        // Procura pelo operador de adição ou subtração mais a direita para precedência mais baixa
        int parenthesesCount = 0;
        int lastAddSubPos = -1;
        
        for (int i = expr.length() - 1; i >= 0; i--) {
            char c = expr.charAt(i);
            if (c == ')') parenthesesCount++;
            else if (c == '(') parenthesesCount--;
            else if ((c == '+' || c == '-') && parenthesesCount == 0) {
                lastAddSubPos = i;
                break;
            }
        }
        
        // Se encontrou operador de adição ou subtração divide a expressão
        if (lastAddSubPos != -1) {
            String leftExpr = expr.substring(0, lastAddSubPos);
            String rightExpr = expr.substring(lastAddSubPos + 1);
            return new OperatorNode(expr.charAt(lastAddSubPos), parseExpression(leftExpr), parseExpression(rightExpr));
        }
        
        // Procura pelo operador de multiplicação ou divisão mais a direita para precedência média
        parenthesesCount = 0;
        int lastMulDivPos = -1;
        
        for (int i = expr.length() - 1; i >= 0; i--) {
            char c = expr.charAt(i);
            if (c == ')') parenthesesCount++;
            else if (c == '(') parenthesesCount--;
            else if ((c == '*' || c == '/') && parenthesesCount == 0) {
                lastMulDivPos = i;
                break;
            }
        }
        
        // Se encontrou operador de multiplicação ou divisão divide a expressão
        if (lastMulDivPos != -1) {
            String leftExpr = expr.substring(0, lastMulDivPos);
            String rightExpr = expr.substring(lastMulDivPos + 1);
            return new OperatorNode(expr.charAt(lastMulDivPos), parseExpression(leftExpr), parseExpression(rightExpr));
        }
        
        // Trata expressões entre parênteses para maior precedência
        if (expr.startsWith("(") && expr.endsWith(")")) {
            // Verifica se os parênteses estão balanceados
            boolean balanced = true;
            parenthesesCount = 0;
            for (int i = 0; i < expr.length() - 1; i++) {
                if (expr.charAt(i) == '(') parenthesesCount++;
                else if (expr.charAt(i) == ')') parenthesesCount--;
                if (parenthesesCount == 0 && i < expr.length() - 1) {
                    balanced = false;
                    break;
                }
            }
            if (balanced) {
                return parseExpression(expr.substring(1, expr.length() - 1));
            }
        }
        
        // Deve ser um valor constante ou variável
        try {
            // Tenta interpretar como número
            return new ValueNode(Double.parseDouble(expr));
        } catch (NumberFormatException e) {
            // Se não for número, é uma variável
            return new VariableNode(expr);
        }
    }
    
    // Encerra o avaliador de forma segura esperando todas as tarefas serem concluídas
    public void shutdown() throws InterruptedException {
        synchronized (lock) {
            if (pendingTasks > 0) {
                lock.wait();
            }
        }
        threadPool.shutdown();
    }

    // Interface para todos os nós da árvore de expressão
    private interface Node {}
    
    // Nó que representa um valor constante
    private static class ValueNode implements Node {
        private final double value;
        
        public ValueNode(double value) {
            this.value = value;
        }
        
        public double getValue() {
            return value;
        }
    }
    
    // Nó que representa uma variável
    private static class VariableNode implements Node {
        private final String name;
        
        public VariableNode(String name) {
            this.name = name;
        }
        
        public String getName() {
            return name;
        }
    }
    
    // Nó que representa uma operação com operador e dois operandos
    private static class OperatorNode implements Node {
        private final char operator;
        private final Node left;
        private final Node right;
        
        public OperatorNode(char operator, Node left, Node right) {
            this.operator = operator;
            this.left = left;
            this.right = right;
        }
        
        public char getOperator() {
            return operator;
        }
        
        public Node getLeft() {
            return left;
        }
        
        public Node getRight() {
            return right;
        }
    }
} 