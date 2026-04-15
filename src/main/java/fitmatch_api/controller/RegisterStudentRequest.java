package fitmatch_api.controller;

public record RegisterStudentRequest(
        String name,
        String email,
        String password,
        String objetivos,
        String nivel
) {}

