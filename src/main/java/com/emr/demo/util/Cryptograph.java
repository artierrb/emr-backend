package com.emr.demo.util;

public class Cryptograph {

    public static String encrypt(String userId, String password) {
        if (userId == null || userId.isEmpty() || password == null) return "";
        StringBuilder result = new StringBuilder();
        int idLen = userId.length();
        for (int i = 0; i < password.length(); i++) {
            int iPos = i % idLen;
            char idChar  = userId.charAt(iPos);
            char pwdChar = password.charAt(i);
            char encChar = (idChar == pwdChar) ? idChar : (char)(idChar ^ pwdChar);
            result.append(encChar);
        }
        return result.toString();
    }

    public static boolean verify(String userId, String plainPassword, String stored) {
        if (stored == null) return false;
        return encrypt(userId, plainPassword).equals(stored);
    }
}
