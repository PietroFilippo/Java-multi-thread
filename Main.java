// Classe principal que demonstra o uso do avaliador de expressões
public class Main {
    public static void main(String[] args) {
        try {
            // Exemplo de expressão: x = ((a+b) * (c-d) / (a+a) * (d-a)) + 4 * 2
            String expression = "((a+b)*(c-d)/(a+a)*(d-a))+4*2";
            
            // Cria uma instância do avaliador de expressões
            ExpressionEvaluator evaluator = new ExpressionEvaluator();
            
            // Define os valores das variáveis
            evaluator.setVariable("a", 7);
            evaluator.setVariable("b", 3);
            evaluator.setVariable("c", 7);
            evaluator.setVariable("d", 4);
            
            // Avalia a expressão e obtém o resultado
            double result = evaluator.evaluate(expression);
            System.out.println("Resultado de " + expression + " = " + result);
            
            // Encerra o avaliador de forma segura
            evaluator.shutdown();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
} 