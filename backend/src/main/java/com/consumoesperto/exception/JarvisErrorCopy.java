package com.consumoesperto.exception;

/**
 * Textos canónicos do protocolo J.A.R.V.I.S. para erros HTTP (UX humanizada).
 */
public final class JarvisErrorCopy {

    public static final String DUPLICATE_RECORD_MESSAGE =
        "Senhor, este registro já consta em nossa base. Se desejar alterá-lo, tente o comando de edição ou verifique o módulo correspondente.";
    public static final String DUPLICATE_RECORD_INSTRUCAO =
        "Abra o módulo certo no painel (ou use *editar* no item existente) e tente novamente.";

    public static final String AUTH_DENIED_MESSAGE =
        "Protocolo de segurança negado. Senhor, sua sessão pode ter expirado ou o acesso não foi autorizado.";
    public static final String AUTH_DENIED_INSTRUCAO =
        "Por favor, revalide suas credenciais — faça login novamente se necessário.";

    public static final String SERVER_INSTABILITY_MESSAGE =
        "Detectei uma instabilidade nos meus sub-processos. Já registrei a falha para reparo.";
    public static final String SERVER_INSTABILITY_INSTRUCAO =
        "Por favor, tente novamente em alguns instantes.";

    public static final String VALIDATION_SUFFIX =
        " Por favor, verifique se o formato está correto para prosseguirmos.";
    public static final String VALIDATION_INSTRUCAO =
        "Corrija o campo indicado e envie de novo.";

    public static final String GENERIC_BAD_REQUEST_INSTRUCAO =
        "Verifique os dados e tente de novo. Se usar o WhatsApp, reformule com valor e descrição claros.";

    public static final String NOT_FOUND_INSTRUCAO =
        "Confirme se o item ainda existe ou atualize a lista no app.";

    public static final String CONFLICT_INSTRUCAO =
        "O estado atual não permite esta operação. Atualize a tela ou tente outro caminho.";

    private JarvisErrorCopy() {
    }
}
