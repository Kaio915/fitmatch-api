package fitmatch_api.controller;

public record RegisterTrainerRequest(
    String nome,
    String email,
    String senha,
    String cref,
    String cidade,
    String especialidade,
    String experiencia,
    String valorHora,
    String bio
) {}


