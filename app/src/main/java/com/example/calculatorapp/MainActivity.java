package com.example.calculatorapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;

// Student: Cem Cakir, Student ID: 44463, Lab Task 3
public class MainActivity extends AppCompatActivity {

    private TextView display;

    // --- Calculator state ---
    private double storedValue = 0;   // left operand / running result
    private String currentInput = ""; // digits being typed right now
    private String pendingOp = null;  // operator waiting to be applied
    private boolean justEqualed = false; // true immediately after "="
    private boolean isError = false;     // error state (div-by-zero, bad conversion)

    // --- History ---
    private final ArrayList<String> history = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        display = findViewById(R.id.display);

        // --- Digit buttons (index matches digit value 0–9) ---
        int[] digitButtonIds = {
            R.id.btn_0, R.id.btn_1, R.id.btn_2, R.id.btn_3, R.id.btn_4,
            R.id.btn_5, R.id.btn_6, R.id.btn_7, R.id.btn_8, R.id.btn_9
        };
        for (int i = 0; i < digitButtonIds.length; i++) {
            final String digit = String.valueOf(i);
            findViewById(digitButtonIds[i]).setOnClickListener(v -> onDigit(digit));
        }

        // --- Operator buttons ---
        findViewById(R.id.btn_add).setOnClickListener(v -> onOperator("+"));
        findViewById(R.id.btn_sub).setOnClickListener(v -> onOperator("-"));
        findViewById(R.id.btn_mul).setOnClickListener(v -> onOperator("*"));
        findViewById(R.id.btn_div).setOnClickListener(v -> onOperator("/"));
        findViewById(R.id.btn_pow).setOnClickListener(v -> onOperator("^"));

        // --- Equals & Clear ---
        findViewById(R.id.btn_eq).setOnClickListener(v -> onEquals());
        findViewById(R.id.btn_c).setOnClickListener(v -> onClear());

        // --- Base conversion ---
        findViewById(R.id.btn_bin).setOnClickListener(v -> onBaseConvert(2));
        findViewById(R.id.btn_oct).setOnClickListener(v -> onBaseConvert(8));
        findViewById(R.id.btn_hex).setOnClickListener(v -> onBaseConvert(16));

        // --- History screen ---
        findViewById(R.id.btn_hist).setOnClickListener(v -> openHistory());

        // --- Expression calculator (Shunting-Yard, correct precedence) ---
        findViewById(R.id.btn_expr).setOnClickListener(
                v -> startActivity(new Intent(this, ExpressionActivity.class)));
    }

    // -------------------------------------------------------------------------
    // Input handlers
    // -------------------------------------------------------------------------

    /**
     * Digit pressed (0–9).
     * After "=": resets accumulated state, starts a fresh number.
     * Leading-zero guard: replaces a lone "0" rather than appending.
     */
    private void onDigit(String digit) {
        if (isError) return;

        if (justEqualed) {
            storedValue = 0;
            pendingOp = null;
            justEqualed = false;
        }

        if (currentInput.isEmpty() || currentInput.equals("0")) {
            currentInput = digit;
        } else {
            currentInput += digit;
        }

        updateDisplay();
    }

    /**
     * Operator pressed (+, -, *, /, ^).
     * If there is already a pending operator and a typed right-hand operand,
     * compute left-to-right first, then register the new operator.
     * Pressing an operator immediately after another operator just replaces it.
     */
    private void onOperator(String op) {
        if (isError) return;

        if (!currentInput.isEmpty()) {
            double inputVal = Double.parseDouble(currentInput);

            if (pendingOp != null) {
                double result = compute(pendingOp, storedValue, inputVal);
                if (isError) return;
                storedValue = result;
            } else {
                storedValue = inputVal;
            }
            currentInput = "";
        }

        pendingOp = op;
        justEqualed = false;
        updateDisplay();
    }

    /**
     * "=" pressed.
     * Applies the pending operator to (storedValue, currentInput) and records
     * the expression in history. Sets justEqualed so the next digit starts fresh
     * while the next operator continues from the result.
     */
    private void onEquals() {
        if (isError) return;

        if (pendingOp != null && !currentInput.isEmpty()) {
            double inputVal = Double.parseDouble(currentInput);

            // Capture operands for history before computing
            String leftStr  = formatValue(storedValue);
            String opSym    = opSymbol(pendingOp);
            String rightStr = currentInput;

            double result = compute(pendingOp, storedValue, inputVal);
            if (isError) return; // div-by-zero; nothing added to history

            history.add(leftStr + " " + opSym + " " + rightStr + " = " + formatValue(result));

            storedValue = result;
            currentInput = "";
            pendingOp = null;
            justEqualed = true;
            updateDisplay();

        } else if (pendingOp == null && !currentInput.isEmpty()) {
            // Bare number with no operator — just confirm the value
            storedValue = Double.parseDouble(currentInput);
            currentInput = "";
            justEqualed = true;
            updateDisplay();
        }
    }

    /**
     * "C" — full reset; the only way to recover from an error state.
     */
    private void onClear() {
        storedValue = 0;
        currentInput = "";
        pendingOp = null;
        justEqualed = false;
        isError = false;
        display.setText("0");
    }

    // -------------------------------------------------------------------------
    // Base conversion
    // -------------------------------------------------------------------------

    /**
     * Converts the current displayed value to the given base (2, 8, or 16) and
     * shows the result. Only works on whole numbers; otherwise shows "Error".
     * Calculator state (storedValue, pendingOp, etc.) is NOT modified, so the
     * user can continue calculating after viewing the converted form.
     *
     * @param base 2 = binary, 8 = octal, 16 = hexadecimal
     */
    private void onBaseConvert(int base) {
        if (isError) return;

        // Resolve the value to convert: prefer what the user is actively typing
        double val = currentInput.isEmpty() ? storedValue : Double.parseDouble(currentInput);

        // Must be a whole number within long range
        if (val != Math.floor(val) || Double.isNaN(val) || Double.isInfinite(val)
                || Math.abs(val) > 9.007199254740992e15) {
            isError = true;
            display.setText("Error");
            return;
        }

        long longVal = (long) val;
        String sign   = longVal < 0 ? "-" : "";
        long   absVal = Math.abs(longVal);

        String converted;
        switch (base) {
            case 2:
                converted = sign + "0b" + Long.toBinaryString(absVal);
                break;
            case 8:
                converted = (absVal == 0) ? "0" : sign + "0" + Long.toOctalString(absVal);
                break;
            case 16:
                converted = sign + "0x" + Long.toHexString(absVal).toUpperCase();
                break;
            default:
                converted = Long.toString(longVal);
        }

        display.setText(converted);
        // Note: intentionally does NOT call updateDisplay() so the result stays
        // visible until the user presses another button.
    }

    // -------------------------------------------------------------------------
    // History
    // -------------------------------------------------------------------------

    private void openHistory() {
        Intent intent = new Intent(this, HistoryActivity.class);
        intent.putStringArrayListExtra("history", history);
        startActivity(intent);
    }

    // -------------------------------------------------------------------------
    // Core arithmetic (strictly left-to-right; no operator precedence)
    // -------------------------------------------------------------------------

    private double compute(String op, double a, double b) {
        switch (op) {
            case "+": return a + b;
            case "-": return a - b;
            case "*": return a * b;
            case "/":
                if (b == 0) {
                    isError = true;
                    display.setText("Error");
                    return 0;
                }
                return a / b;
            case "^": return Math.pow(a, b);
            default:  return b;
        }
    }

    // -------------------------------------------------------------------------
    // Display helpers
    // -------------------------------------------------------------------------

    /** Shows currentInput while typing; falls back to storedValue otherwise. */
    private void updateDisplay() {
        if (isError) {
            display.setText("Error");
        } else if (!currentInput.isEmpty()) {
            display.setText(currentInput);
        } else {
            display.setText(formatValue(storedValue));
        }
    }

    /** Strips the ".0" suffix from whole-number doubles (8.0 → "8", 7.5 → "7.5"). */
    private String formatValue(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            isError = true;
            return "Error";
        }
        if (v == Math.floor(v) && Math.abs(v) < 1e15) {
            return Long.toString((long) v);
        }
        return Double.toString(v);
    }

    /** Returns a human-readable operator symbol for history entries. */
    private String opSymbol(String op) {
        switch (op) {
            case "+": return "+";
            case "-": return "−";
            case "*": return "×";
            case "/": return "÷";
            case "^": return "^";
            default:  return op;
        }
    }
}
