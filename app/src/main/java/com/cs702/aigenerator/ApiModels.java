package com.cs702.aigenerator;

import com.google.gson.annotations.SerializedName;

class AuthResponse {
    @SerializedName("signature")
    private String signature;
    public String getSignature() { return signature; }
}

class GenerateRequest {
    @SerializedName("signature")
    private String signature;
    @SerializedName("prompt")
    private String prompt;

    public GenerateRequest(String signature, String prompt) {
        this.signature = signature;
        this.prompt = prompt;
    }
}

class GenerateResponse {
    @SerializedName("image_url")
    private String imageUrl;
    public String getImageUrl() { return imageUrl; }
}