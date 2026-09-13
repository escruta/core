package com.escruta.core.dtos;

public record VerifyEmailCodeResponse(
        boolean newUser,
        AccessTokenResponse session,
        String verificationToken,
        BasicUser user
) {
    public static VerifyEmailCodeResponse existingUser(AccessTokenResponse session, BasicUser user) {
        return new VerifyEmailCodeResponse(false, session, null, user);
    }

    public static VerifyEmailCodeResponse newUser(String verificationToken) {
        return new VerifyEmailCodeResponse(true, null, verificationToken, null);
    }
}
