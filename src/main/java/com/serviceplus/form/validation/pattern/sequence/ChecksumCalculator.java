package com.serviceplus.form.validation.pattern.sequence;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ChecksumCalculator {

    private static final Logger log = LogManager.getLogger("applicationFlowLogger");

    // Verhoeff multiplication table
    private static final int[][] VERHOEFF_D = {
            {0, 1, 2, 3, 4, 5, 6, 7, 8, 9},
            {1, 2, 3, 4, 0, 6, 7, 8, 9, 5},
            {2, 3, 4, 0, 1, 7, 8, 9, 5, 6},
            {3, 4, 0, 1, 2, 8, 9, 5, 6, 7},
            {4, 0, 1, 2, 3, 9, 5, 6, 7, 8},
            {5, 9, 8, 7, 6, 0, 4, 3, 2, 1},
            {6, 5, 9, 8, 7, 1, 0, 4, 3, 2},
            {7, 6, 5, 9, 8, 2, 1, 0, 4, 3},
            {8, 7, 6, 5, 9, 3, 2, 1, 0, 4},
            {9, 8, 7, 6, 5, 4, 3, 2, 1, 0}
    };

    // Verhoeff permutation table
    private static final int[][] VERHOEFF_P = {
            {0, 1, 2, 3, 4, 5, 6, 7, 8, 9},
            {1, 5, 7, 6, 2, 8, 3, 0, 9, 4},
            {5, 8, 0, 3, 7, 9, 6, 1, 4, 2},
            {8, 9, 1, 6, 0, 4, 3, 5, 2, 7},
            {9, 4, 5, 3, 1, 2, 6, 8, 7, 0},
            {4, 2, 8, 6, 5, 7, 3, 9, 0, 1},
            {2, 7, 9, 3, 8, 0, 6, 4, 1, 5},
            {7, 0, 4, 6, 9, 1, 3, 2, 5, 8}
    };

    // Verhoeff inverse table
    private static final int[] VERHOEFF_INV = {0, 4, 3, 2, 1, 5, 6, 7, 8, 9};

    public static String computeChecksum(String input, String algorithm) {
        log.info("ChecksumCalculator: computeChecksum requested for algorithm '{}' on input '{}'", algorithm, input);
        if (input == null || algorithm == null || "NONE".equalsIgnoreCase(algorithm)) {
            log.info("Checksum calculation skipped (null or NONE)");
            return "";
        }
        String checkDigit = "";
        // Extract numeric digits or compute based on character ASCII values
        if ("LUHN".equalsIgnoreCase(algorithm)) {
            checkDigit = String.valueOf(calculateLuhnCheckDigit(input));
        } else if ("VERHOEFF".equalsIgnoreCase(algorithm)) {
            checkDigit = String.valueOf(calculateVerhoeffCheckDigit(input));
        }
        log.info("ChecksumCalculator: Computed check digit '{}' using algorithm '{}'", checkDigit, algorithm);
        return checkDigit;
    }

    public static int calculateLuhnCheckDigit(String input) {
        // Extract digits or convert chars to digits mod 10
        StringBuilder digits = new StringBuilder();
        for (char c : input.toCharArray()) {
            if (Character.isDigit(c)) {
                digits.append(c);
            } else if (Character.isLetter(c)) {
                digits.append((c - 'A' + 1) % 10);
            }
        }
        String numStr = digits.toString();
        if (numStr.isEmpty()) {
            return 0;
        }

        int sum = 0;
        boolean alternate = true;
        for (int i = numStr.length() - 1; i >= 0; i--) {
            int n = Integer.parseInt(numStr.substring(i, i + 1));
            if (alternate) {
                n *= 2;
                if (n > 9) {
                    n = (n % 10) + 1;
                }
            }
            sum += n;
            alternate = !alternate;
        }
        return (10 - (sum % 10)) % 10;
    }

    public static int calculateVerhoeffCheckDigit(String input) {
        StringBuilder digits = new StringBuilder();
        for (char c : input.toCharArray()) {
            if (Character.isDigit(c)) {
                digits.append(c);
            } else if (Character.isLetter(c)) {
                digits.append((c - 'A' + 1) % 10);
            }
        }
        String numStr = digits.toString();
        if (numStr.isEmpty()) {
            return 0;
        }

        int c = 0;
        int length = numStr.length();
        for (int i = 0; i < length; i++) {
            int digit = Character.getNumericValue(numStr.charAt(length - 1 - i));
            c = VERHOEFF_D[c][VERHOEFF_P[(i + 1) % 8][digit]];
        }
        return VERHOEFF_INV[c];
    }
}
