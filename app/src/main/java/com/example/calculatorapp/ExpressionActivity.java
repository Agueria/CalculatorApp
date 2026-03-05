package com.example.calculatorapp;

import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * Expression calculator that builds a full infix expression string and
 * evaluates it with correct operator precedence using the Shunting-Yard
 * algorithm.  2 + 2 * 2 → 6, not 8.
 *
 * Operator precedence (low → high):
 *   +, -  (1)  left-associative
 *   *, /  (2)  left-associative
 *   ^     (3)  RIGHT-associative  →  2^3^2 = 2^(3^2) = 512
 */
public class ExpressionActivity extends AppCompatActivity {

    // Secondary display: shows "expr =" after evaluation (gray, smaller)
    private TextView tvHistory;
    // Primary display: shows expression while typing, result after "="
    private TextView tvDisplay;

    // Raw infix expression being typed, using +-*/^ as operator chars
    private final StringBuilder expression = new StringBuilder();
    // Stored for "continue after =" with an operator press
    private double lastResult = 0;
    // True immediately after "=" — next digit starts fresh, next op continues
    private boolean justEvaled = false;
    // True on evaluation error (div-by-zero, malformed expr); C to reset
    private boolean hasError = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_expression);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.expr_root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        tvHistory = findViewById(R.id.tv_history);
        tvDisplay = findViewById(R.id.tv_display);

        // --- Digit buttons (index == digit value) ---
        int[] digitIds = {
            R.id.eb_0, R.id.eb_1, R.id.eb_2, R.id.eb_3, R.id.eb_4,
            R.id.eb_5, R.id.eb_6, R.id.eb_7, R.id.eb_8, R.id.eb_9
        };
        for (int i = 0; i < digitIds.length; i++) {
            final String d = String.valueOf(i);
            findViewById(digitIds[i]).setOnClickListener(v -> appendDigit(d));
        }

        // --- Decimal ---
        findViewById(R.id.eb_dot).setOnClickListener(v -> appendDot());

        // --- Operators ---
        findViewById(R.id.eb_add).setOnClickListener(v -> appendOperator("+"));
        findViewById(R.id.eb_sub).setOnClickListener(v -> appendOperator("-"));
        findViewById(R.id.eb_mul).setOnClickListener(v -> appendOperator("*"));
        findViewById(R.id.eb_div).setOnClickListener(v -> appendOperator("/"));
        findViewById(R.id.eb_pow).setOnClickListener(v -> appendOperator("^"));

        // --- Actions ---
        findViewById(R.id.eb_eq).setOnClickListener(v -> onEquals());
        findViewById(R.id.eb_c).setOnClickListener(v -> onClear());
        findViewById(R.id.eb_del).setOnClickListener(v -> onDel());
    }

    // -------------------------------------------------------------------------
    // Expression building
    // -------------------------------------------------------------------------

    private void appendDigit(String d) {
        if (hasError) return;
        if (justEvaled) {
            // Start a fresh expression; discard the previous result
            expression.setLength(0);
            tvHistory.setText("");
            justEvaled = false;
        }

        // Replace a bare "0" trailing the expression (avoids "007" style input)
        if ("0".equals(lastNumberToken())) {
            expression.deleteCharAt(expression.length() - 1);
        }

        expression.append(d);
        updateDisplay();
    }

    private void appendDot() {
        if (hasError) return;
        if (justEvaled) {
            expression.setLength(0);
            expression.append("0");
            tvHistory.setText("");
            justEvaled = false;
        }
        // Only one decimal point per number token
        if (lastNumberToken().contains(".")) return;
        // Prefix an implicit "0" when the dot follows an operator (e.g. "3+." → "3+0.")
        if (lastNumberToken().isEmpty()) expression.append("0");
        expression.append(".");
        updateDisplay();
    }

    private void appendOperator(String op) {
        if (hasError) return;

        if (justEvaled) {
            // Continue from the last result (e.g. "5 =", then press "+")
            expression.setLength(0);
            expression.append(formatValue(lastResult));
            tvHistory.setText("");
            justEvaled = false;
        }

        // Do not allow an operator as the very first character
        if (expression.length() == 0) return;

        // Replace a consecutive operator rather than stacking two
        if (endsWithOperator()) {
            expression.setCharAt(expression.length() - 1, op.charAt(0));
        } else {
            expression.append(op);
        }

        updateDisplay();
    }

    // -------------------------------------------------------------------------
    // Actions
    // -------------------------------------------------------------------------

    /** Evaluates the current expression with correct operator precedence. */
    private void onEquals() {
        if (hasError || justEvaled || expression.length() == 0) return;

        // Strip any trailing operator before evaluating
        String raw = expression.toString();
        if (endsWithOperator()) raw = raw.substring(0, raw.length() - 1);
        if (raw.isEmpty()) return;

        try {
            lastResult = evaluate(raw);
            tvHistory.setText(prettyExpr(raw) + "  =");
            tvDisplay.setText(formatValue(lastResult));
            expression.setLength(0);
            justEvaled = true;
        } catch (ArithmeticException e) {
            // Division by zero
            hasError = true;
            tvHistory.setText("");
            tvDisplay.setText("Error");
        } catch (Exception e) {
            hasError = true;
            tvHistory.setText("");
            tvDisplay.setText("Syntax error");
        }
    }

    /** Full reset — the only way to exit an error state. */
    private void onClear() {
        expression.setLength(0);
        lastResult = 0;
        justEvaled = false;
        hasError = false;
        tvHistory.setText("");
        tvDisplay.setText("0");
    }

    /** Backspace: removes the last character; resets on error or after "=". */
    private void onDel() {
        if (hasError || justEvaled) {
            onClear();
            return;
        }
        if (expression.length() > 0) {
            expression.deleteCharAt(expression.length() - 1);
        }
        updateDisplay();
    }

    // -------------------------------------------------------------------------
    // Shunting-Yard evaluation
    // -------------------------------------------------------------------------

    /**
     * Full pipeline: tokenise → Shunting-Yard (infix → RPN) → evaluate RPN.
     */
    private double evaluate(String expr) throws Exception {
        List<String> tokens = tokenize(expr);
        if (tokens.isEmpty()) throw new Exception("Empty expression");
        Queue<String> rpn = shuntingYard(tokens);
        return evalRPN(rpn);
    }

    /**
     * Splits the expression string into a list of number and operator tokens.
     * Numbers may contain a decimal point; operators are single characters.
     */
    private List<String> tokenize(String expr) {
        List<String> tokens = new ArrayList<>();
        int i = 0;
        while (i < expr.length()) {
            char c = expr.charAt(i);
            if (Character.isDigit(c) || c == '.') {
                // Consume a full number token (integer or decimal)
                StringBuilder sb = new StringBuilder();
                while (i < expr.length()
                        && (Character.isDigit(expr.charAt(i)) || expr.charAt(i) == '.')) {
                    sb.append(expr.charAt(i++));
                }
                tokens.add(sb.toString());
            } else if ("+-*/^".indexOf(c) >= 0) {
                tokens.add(String.valueOf(c));
                i++;
            } else {
                i++; // skip unrecognised characters
            }
        }
        return tokens;
    }

    /**
     * Dijkstra's Shunting-Yard algorithm.
     *
     * Converts the infix token list to a Reverse Polish Notation queue.
     * Correctly handles:
     *   - Left-associative operators (+, -, *, /)
     *   - Right-associative operator (^)
     *   - Precedence: + - < * / < ^
     */
    private Queue<String> shuntingYard(List<String> tokens) {
        Queue<String> output = new LinkedList<>();
        Deque<String> opStack = new ArrayDeque<>();

        for (String token : tokens) {
            if (isNumber(token)) {
                output.add(token);
            } else if (isOp(token)) {
                // Pop operators from the stack while they should be evaluated first
                while (!opStack.isEmpty() && isOp(opStack.peek())) {
                    String top = opStack.peek();
                    boolean shouldPop = isLeftAssoc(token)
                            ? prec(top) >= prec(token)
                            : prec(top) >  prec(token); // right-assoc: strict greater-than
                    if (shouldPop) {
                        output.add(opStack.pop());
                    } else {
                        break;
                    }
                }
                opStack.push(token);
            }
        }

        // Drain any remaining operators
        while (!opStack.isEmpty()) {
            output.add(opStack.pop());
        }
        return output;
    }

    /**
     * Evaluates an RPN token queue using a value stack.
     * For each binary operator, pops two operands: 'b' then 'a', computes a OP b.
     */
    private double evalRPN(Queue<String> rpn) throws Exception {
        Deque<Double> stack = new ArrayDeque<>();
        for (String token : rpn) {
            if (isNumber(token)) {
                stack.push(Double.parseDouble(token));
            } else {
                if (stack.size() < 2) throw new Exception("Bad expression");
                double b = stack.pop(); // right operand
                double a = stack.pop(); // left operand
                stack.push(applyOp(token, a, b));
            }
        }
        if (stack.size() != 1) throw new Exception("Bad expression");
        return stack.pop();
    }

    private double applyOp(String op, double a, double b) throws Exception {
        switch (op) {
            case "+": return a + b;
            case "-": return a - b;
            case "*": return a * b;
            case "/":
                if (b == 0) throw new ArithmeticException("Division by zero");
                return a / b;
            case "^": return Math.pow(a, b);
            default:  throw new Exception("Unknown operator: " + op);
        }
    }

    // -------------------------------------------------------------------------
    // Operator metadata
    // -------------------------------------------------------------------------

    private int prec(String op) {
        switch (op) {
            case "+": case "-": return 1;
            case "*": case "/": return 2;
            case "^":           return 3;
            default:            return 0;
        }
    }

    /** Returns false only for ^, which is right-associative. */
    private boolean isLeftAssoc(String op) {
        return !op.equals("^");
    }

    private boolean isOp(String s) {
        return "+".equals(s) || "-".equals(s) || "*".equals(s)
                || "/".equals(s) || "^".equals(s);
    }

    private boolean isNumber(String s) {
        try { Double.parseDouble(s); return true; }
        catch (NumberFormatException e) { return false; }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the number token currently being typed — the substring after the
     * last operator in the expression.  Empty string if nothing typed yet or
     * the expression ends with an operator.
     */
    private String lastNumberToken() {
        String s = expression.toString();
        for (int i = s.length() - 1; i >= 0; i--) {
            if ("+-*/^".indexOf(s.charAt(i)) >= 0) {
                return s.substring(i + 1);
            }
        }
        return s; // entire expression is a number (no operator found)
    }

    private boolean endsWithOperator() {
        if (expression.length() == 0) return false;
        return "+-*/^".indexOf(expression.charAt(expression.length() - 1)) >= 0;
    }

    /** Replaces raw * and / with × and ÷ for display only. */
    private String prettyExpr(String s) {
        return s.replace("*", "×").replace("/", "÷");
    }

    private void updateDisplay() {
        String s = expression.toString();
        tvDisplay.setText(s.isEmpty() ? "0" : prettyExpr(s));
    }

    /** Strips ".0" suffix from whole-number doubles (8.0 → "8", 7.5 → "7.5"). */
    private String formatValue(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "Error";
        if (v == Math.floor(v) && Math.abs(v) < 1e15) return Long.toString((long) v);
        return Double.toString(v);
    }
}
