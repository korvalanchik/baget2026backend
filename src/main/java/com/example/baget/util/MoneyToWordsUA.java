package com.example.baget.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class MoneyToWordsUA {
    private static final String[] UNITS = {
            "", "одна", "дві", "три", "чотири", "п’ять",
            "шість", "сім", "вісім", "дев’ять", "десять",
            "одинадцять", "дванадцять", "тринадцять", "чотирнадцять",
            "п’ятнадцять", "шістнадцять", "сімнадцять", "вісімнадцять", "дев’ятнадцять"
    };

    private static final String[] TENS = {
            "", "десять", "двадцять", "тридцять", "сорок",
            "п’ятдесят", "шістдесят", "сімдесят", "вісімдесят", "дев’яносто"
    };

    private static final String[] HUNDREDS = {
            "", "сто", "двісті", "триста", "чотириста",
            "п’ятсот", "шістсот", "сімсот", "вісімсот", "дев’ятсот"
    };

    private static final String[][] FORMS = {
            {"копійка", "копійки", "копійок"},
            {"гривня", "гривні", "гривень"},
            {"тисяча", "тисячі", "тисяч"},
            {"мільйон", "мільйони", "мільйонів"},
            {"мільярд", "мільярди", "мільярдів"}
    };

    public static String convert(BigDecimal amount) {
        if (amount == null) {
            return "";
        }

        // гривні
        long hryvnias = amount.longValue();

        // копійки (залишок після коми)
        BigDecimal fractionalPart = amount.subtract(BigDecimal.valueOf(hryvnias));
        long kopiykas = fractionalPart
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();

        String hryvniasPart = numToWords(hryvnias);
        String kopiykasPart = String.format("%02d %s", kopiykas, getForm(kopiykas, 0));

        return capitalizeFirstLetter(hryvniasPart + " " + kopiykasPart);
    }

    private static String numToWords(long num) {
        if (num == 0) {
            return "нуль " + getForm(0, 1);
        }

        StringBuilder words = new StringBuilder();
        int level = 0;

        while (num > 0) {
            int n = (int) (num % 1000);
            if (n != 0) {
                String segment = threeDigitsToWords(n, level == 1);
                words.insert(0, segment + " " + getForm(n, level + 1) + " ");
            }
            num /= 1000;
            level++;
        }

        return words.toString().trim();
    }

    private static String threeDigitsToWords(int num, boolean female) {
        StringBuilder sb = new StringBuilder();

        if (num >= 100) {
            sb.append(HUNDREDS[num / 100]).append(" ");
            num %= 100;
        }

        if (num >= 20) {
            sb.append(TENS[num / 10]).append(" ");
            num %= 10;
        }

        if (num > 0) {
            if (num == 1 && female) {
                sb.append("одна");
            } else if (num == 2 && female) {
                sb.append("дві");
            } else {
                sb.append(UNITS[num]);
            }
            sb.append(" ");
        }

        return sb.toString().trim();
    }

    private static String getForm(long number, int formIndex) {
        int n = (int) (number % 100);
        if (n >= 11 && n <= 19) {
            return FORMS[formIndex][2];
        }
        return switch (n % 10) {
            case 1 -> FORMS[formIndex][0];
            case 2, 3, 4 -> FORMS[formIndex][1];
            default -> FORMS[formIndex][2];
        };
    }

    private static String capitalizeFirstLetter(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    public static void main(String[] args) {
        System.out.println(System.getProperty("file.encoding"));
        System.out.println("Привіт, Україно!");
        System.out.println(MoneyToWordsUA.convert(new BigDecimal("1560.0")));   // Одна тисяча п’ятсот шістдесят гривень нуль копійок
        System.out.println(MoneyToWordsUA.convert(new BigDecimal("98.25")));    // Дев’яносто вісім гривень двадцять п’ять копійок
        System.out.println(MoneyToWordsUA.convert(new BigDecimal("1.01")));     // Одна гривня одна копійка
        System.out.println(MoneyToWordsUA.convert(new BigDecimal("30565.15")));     // Одна гривня одна копійка
    }
}
