-- Baseline Hibernate — entidades JPA: 36


    create table agendamentos_pagamentos (
       id  bigserial not null,
        beneficiario varchar(200) not null,
        codigo_barras_ou_pix TEXT,
        data_criacao timestamp not null,
        data_fim date,
        data_processamento timestamp,
        data_vencimento date not null,
        dia_vencimento_mensal int4,
        falhas_consecutivas int4 not null,
        mensagem_erro varchar(500),
        proxima_execucao date,
        recorrencia varchar(16),
        status varchar(16) not null,
        ultima_chave_execucao varchar(64),
        ultima_execucao date,
        valor numeric(19, 2) not null,
        cartao_credito_id int8,
        categoria_id int8,
        conta_debito_id int8 not null,
        usuario_id int8 not null,
        primary key (id)
    );

    create table assinaturas_recorrentes (
       id  bigserial not null,
        ativo boolean not null,
        data_atualizacao timestamp not null,
        data_criacao timestamp not null,
        dia_vencimento int4 not null,
        nome varchar(200) not null,
        valor numeric(19, 2) not null,
        conta_debito_padrao_id int8,
        usuario_id int8 not null,
        primary key (id)
    );

    create table audit_log (
       id  bigserial not null,
        created_at timestamp not null,
        descricao TEXT not null,
        tipo varchar(64),
        usuario_id int8 not null,
        primary key (id)
    );

    create table cartoes_credito (
       id  bigserial not null,
        ativo boolean,
        banco varchar(255),
        cor varchar(255),
        data_criacao timestamp,
        dia_vencimento int4,
        icone varchar(255),
        limite_credito numeric(19, 2),
        limite_disponivel numeric(19, 2),
        nome varchar(255),
        numero_cartao varchar(255),
        usuario_id int8,
        primary key (id)
    );

    create table categorias (
       id  bigserial not null,
        ativo boolean,
        cor varchar(255),
        data_criacao timestamp,
        descricao varchar(255),
        icone varchar(255),
        nome varchar(255),
        usuario_id int8,
        primary key (id)
    );

    create table compras_parceladas (
       id  bigserial not null,
        ativo boolean,
        data_atualizacao timestamp,
        data_compra timestamp,
        data_criacao timestamp,
        data_primeira_parcela timestamp,
        data_ultima_parcela timestamp,
        descricao varchar(255),
        numero_parcelas int4,
        parcela_atual int4,
        status_compra varchar(255),
        valor_parcela numeric(19, 2),
        valor_total numeric(19, 2),
        cartao_credito_id int8,
        categoria_id int8,
        usuario_id int8,
        primary key (id)
    );

    create table contas_bancarias (
       id  bigserial not null,
        ativa boolean not null,
        data_atualizacao timestamp not null,
        data_criacao timestamp not null,
        limite_cheque_especial numeric(15, 2) not null,
        nome varchar(255),
        padrao boolean not null,
        saldo_atual numeric(19, 2) not null,
        saldo_inicial numeric(19, 2),
        tipo varchar(20) not null,
        usuario_id int8 not null,
        primary key (id)
    );

    create table contracheque_descontos (
       id  bigserial not null,
        descricao varchar(200) not null,
        valor numeric(19, 2) not null,
        contracheque_importado_id int8 not null,
        primary key (id)
    );

    create table contracheques_importados (
       id  bigserial not null,
        ano int4,
        auditoria_delta_bruto numeric(19, 2),
        auditoria_soma_bruto_ok boolean,
        data_confirmacao timestamp,
        data_criacao timestamp not null,
        descontos_json oid,
        empresa varchar(160),
        insights_json oid,
        mes int4,
        salario_bruto numeric(19, 2),
        salario_liquido numeric(19, 2),
        status varchar(255) not null,
        total_descontos numeric(19, 2),
        usuario_id int8 not null,
        primary key (id)
    );

    create table debitos_internos (
       id  bigserial not null,
        data_criacao timestamp not null,
        data_liquidacao timestamp,
        descricao varchar(200),
        liquidado boolean not null,
        valor numeric(19, 2) not null,
        credor_usuario_id int8 not null,
        devedor_usuario_id int8 not null,
        grupo_familiar_id int8 not null,
        primary key (id)
    );

    create table despesas_fixas (
       id  bigserial not null,
        categoria varchar(120),
        data_atualizacao timestamp not null,
        data_criacao timestamp not null,
        debito_automatico boolean not null,
        descricao varchar(200) not null,
        dia_vencimento int4 not null,
        valor numeric(19, 2) not null,
        conta_bancaria_id int8,
        usuario_id int8 not null,
        primary key (id)
    );

    create table faturas (
       id  bigserial not null,
        data_criacao timestamp,
        data_fechamento timestamp,
        data_pagamento timestamp,
        data_vencimento timestamp,
        numero_fatura varchar(255),
        origem_quitacao varchar(255),
        paga boolean,
        status varchar(255),
        valor_fatura numeric(19, 2),
        valor_minimo numeric(19, 2),
        valor_pago numeric(19, 2),
        valor_total numeric(19, 2),
        cartao_credito_id int8,
        usuario_id int8,
        primary key (id)
    );

    create table grupo_familiar_membros (
       id  bigserial not null,
        convite_email varchar(120),
        convite_whatsapp varchar(32),
        data_convite timestamp not null,
        data_resposta timestamp,
        status varchar(32) not null,
        token_convite varchar(64) not null,
        convidado_por_usuario_id int8 not null,
        grupo_familiar_id int8 not null,
        usuario_id int8,
        primary key (id)
    );

    create table grupos_familiares (
       id  bigserial not null,
        data_criacao timestamp not null,
        nome varchar(120) not null,
        criador_usuario_id int8 not null,
        primary key (id)
    );

    create table historico_score (
       id  bigserial not null,
        data_evento timestamp not null,
        delta int4 not null,
        detalhe varchar(500),
        motivo varchar(120) not null,
        score_resultante int4 not null,
        usuario_id int8 not null,
        primary key (id)
    );

    create table importacoes_fatura_cartao (
       id  bigserial not null,
        auditoria_json oid,
        banco_cartao varchar(120),
        data_confirmacao timestamp,
        data_criacao timestamp not null,
        data_fechamento timestamp,
        data_vencimento timestamp,
        itens_json oid not null,
        novos_detectados int4 not null,
        pagamento_minimo numeric(19, 2),
        status varchar(255) not null,
        valor_total numeric(19, 2),
        cartao_credito_id int8,
        fatura_id int8,
        usuario_id int8 not null,
        primary key (id)
    );

    create table metas_financeiras (
       id  bigserial not null,
        data_criacao timestamp not null,
        data_expiracao date,
        descricao varchar(255),
        google_calendar_event_id varchar(128),
        percentual_comprometimento numeric(5, 2),
        prazo_meses numeric(10, 2),
        prioridade int4 not null,
        renda_media_referencia numeric(19, 2),
        valor_acumulado numeric(19, 2),
        valor_poupado_mensal numeric(19, 2),
        valor_total numeric(19, 2),
        usuario_id int8 not null,
        primary key (id)
    );

    create table movimentacao_saldo_log (
       id  bigserial not null,
        conta_id int8 not null,
        criado_em timestamp not null,
        delta numeric(19, 2) not null,
        origem varchar(20) not null,
        saldo_antes numeric(19, 2) not null,
        saldo_depois numeric(19, 2) not null,
        tipo_operacao varchar(32) not null,
        transacao_id int8,
        usuario_id int8,
        primary key (id)
    );

    create table notificacao_digest_buffer (
       id  bigserial not null,
        criado_em timestamp not null,
        data_ref date not null,
        hash_evento varchar(128) not null,
        linha_digest varchar(500),
        mensagem_completa TEXT,
        tipo varchar(64) not null,
        titulo_web varchar(200),
        usuario_id int8 not null,
        primary key (id)
    );

    create table notificacao_enviada (
       id  bigserial not null,
        categoria varchar(16) not null,
        data_envio timestamp not null,
        hash_evento varchar(128) not null,
        tipo varchar(64) not null,
        usuario_id int8 not null,
        primary key (id)
    );

    create table notificacoes (
       id  bigserial not null,
        data_criacao timestamp,
        data_leitura timestamp,
        lida boolean,
        mensagem TEXT not null,
        tipo varchar(50),
        titulo varchar(200) not null,
        usuario_id int8 not null,
        primary key (id)
    );

    create table notificacoes_fechamento_cartao (
       id  bigserial not null,
        criado_em timestamp not null,
        data_fechamento_referencia date not null,
        mensagem_preview varchar(500),
        tipo varchar(16) not null,
        cartao_credito_id int8 not null,
        primary key (id)
    );

    create table orcamentos (
       id  bigserial not null,
        ano int4 not null,
        compartilhado boolean not null,
        data_atualizacao timestamp not null,
        data_criacao timestamp not null,
        mes int4 not null,
        valor_limite numeric(19, 2) not null,
        categoria_id int8 not null,
        grupo_familiar_id int8,
        usuario_id int8 not null,
        primary key (id)
    );

    create table parcelas (
       id  bigserial not null,
        data_criacao timestamp,
        data_pagamento timestamp,
        data_vencimento timestamp,
        numero_parcela int4,
        status varchar(255),
        valor_pago numeric(19, 2),
        valor_parcela numeric(19, 2),
        compra_parcelada_id int8,
        primary key (id)
    );

    create table rendas (
       id  bigserial not null,
        ativa boolean not null,
        data_atualizacao timestamp not null,
        data_criacao timestamp not null,
        descricao varchar(200) not null,
        dia_pagamento int4 not null,
        ultimo_mes_credito int4,
        valor numeric(19, 2) not null,
        conta_destino_id int8 not null,
        usuario_id int8 not null,
        primary key (id)
    );

    create table sugestoes_contencao_jarvis (
       id  bigserial not null,
        ano_alvo int4 not null,
        chave_agrupamento varchar(200) not null,
        data_criacao timestamp not null,
        media_tres_meses numeric(19, 2),
        mes_alvo int4 not null,
        percentual_aumento numeric(8, 2),
        rotulo_exibicao varchar(260) not null,
        status varchar(16) not null,
        tipo_habito varchar(24) not null,
        valor_gasto_referencia numeric(19, 2) not null,
        valor_teto_sugerido numeric(19, 2) not null,
        categoria_id int8,
        importacao_fatura_cartao_id int8,
        usuario_id int8 not null,
        primary key (id)
    );

    create table transacoes (
       id  bigserial not null,
        cnpj varchar(18),
        data_criacao timestamp,
        data_transacao timestamp,
        desconto_em_folha boolean,
        descricao varchar(255),
        emprestimo_id varchar(36),
        excluido boolean not null,
        frequencia varchar(255),
        grupo_parcela_id varchar(36),
        origem_fiscal varchar(40),
        parcela_atual int4,
        proxima_execucao date,
        recorrente boolean not null,
        status_conferencia varchar(255) not null,
        tipo_transacao varchar(255),
        total_parcelas int4,
        valor numeric(19, 2),
        valor_com_juros numeric(19, 2),
        valor_real numeric(19, 2),
        categoria_id int8,
        conta_bancaria_id int8,
        fatura_id int8,
        usuario_id int8,
        primary key (id)
    );

    create table transferencias_contas (
       id  bigserial not null,
        data_criacao timestamp not null,
        data_transferencia timestamp not null,
        descricao varchar(200),
        valor numeric(19, 2) not null,
        conta_destino_id int8 not null,
        conta_origem_id int8 not null,
        usuario_id int8 not null,
        primary key (id)
    );

    create table usuario_ai_config (
       id  bigserial not null,
        evolution_api_key TEXT,
        evolution_instance_name varchar(128),
        evolution_session_suppressed boolean not null,
        groq_api_key TEXT,
        groq_base_url varchar(500),
        groq_model_text varchar(200),
        groq_model_vision varchar(200),
        groq_whisper_model varchar(200),
        jarvis_notif_prefs_json TEXT,
        ollama_base_url varchar(500),
        ollama_model varchar(200),
        openai_api_key TEXT,
        openai_base_url varchar(500),
        openai_model varchar(200),
        openai_whisper_model varchar(200),
        provider_order varchar(500),
        whatsapp_owner_phone varchar(32),
        usuario_id int8 not null,
        primary key (id)
    );

    create table usuario_configuracao_fiscal (
       id  bigserial not null,
        data_atualizacao timestamp,
        dia_pagamento_13 int4,
        mes_parcela_unica int4,
        mes_primeira_parcela int4,
        mes_restituicao_ir int4,
        mes_segunda_parcela int4,
        provisionamento_ativo boolean not null,
        tipo_recebimento_13 varchar(32),
        valor_restituicao numeric(19, 2),
        usuario_id int8 not null,
        primary key (id)
    );

    create table usuario_perfil_comportamental (
       id  bigserial not null,
        calculado_em timestamp not null,
        confianca_pct int4 not null,
        perfil varchar(32) not null,
        perfil_anterior varchar(32),
        usuario_id int8 not null,
        primary key (id)
    );

    create table usuario_renda_config (
       id  bigserial not null,
        data_atualizacao timestamp,
        descontos_fixos_json TEXT,
        dia_pagamento int4,
        meta_faturamento_mensal numeric(19, 2),
        receita_automatica_ativa boolean not null,
        salario_bruto numeric(19, 2) not null,
        salario_liquido numeric(19, 2) not null,
        tipo_configuracao_renda varchar(30) not null,
        ultimo_mes_lancamento_auto int4,
        valor_recebimento_unico numeric(19, 2),
        conta_bancaria_id int8,
        usuario_id int8 not null,
        primary key (id)
    );

    create table usuario_sessoes_contexto (
       id  bigserial not null,
        atualizado_em timestamp not null,
        canal varchar(32) not null,
        chave_sessao varchar(128) not null,
        contexto_json TEXT not null,
        expira_em timestamp,
        usuario_id int8 not null,
        primary key (id)
    );

    create table usuarios (
       id  bigserial not null,
        data_criacao timestamp,
        email varchar(255),
        email_verificado boolean,
        foto_url TEXT,
        genero varchar(16),
        genero_confirmado boolean,
        genero_gramatical varchar(16),
        google_calendar_linked_at timestamp,
        google_calendar_refresh_token TEXT,
        google_id varchar(255),
        jarvis_configurado boolean not null,
        jarvis_google_genero_notificado boolean,
        locale varchar(10),
        nome varchar(255),
        password varchar(255),
        preferencia_tratamento_jarvis varchar(32),
        provedor_auth varchar(255),
        tratamento varchar(32),
        tratamento_configurado boolean,
        ultimo_acesso timestamp,
        username varchar(255),
        vocativo varchar(64),
        whatsapp_number varchar(20),
        primary key (id)
    );

    create table usuarios_score (
       id  bigserial not null,
        data_atualizacao timestamp not null,
        nivel varchar(40) not null,
        score int4 not null,
        usuario_id int8 not null,
        primary key (id)
    );

    create table whatsapp_lembrete_pendencia (
       id  bigserial not null,
        enviado_em timestamp not null,
        tipo varchar(40) not null,
        transacao_id int8 not null,
        usuario_id int8 not null,
        primary key (id)
    );
create index idx_cont_desc_import_id on contracheque_descontos (contracheque_importado_id);

    alter table faturas 
       add constraint UK_nrxb1meym33xe417rdi2dyxhm unique (numero_fatura);

    alter table grupo_familiar_membros 
       add constraint uk_grupo_membro_usuario unique (grupo_familiar_id, usuario_id);

    alter table grupo_familiar_membros 
       add constraint UK_u6314ivrkkpp6lrha0dpirjd unique (token_convite);
create index idx_notif_digest_usuario_data on notificacao_digest_buffer (usuario_id, data_ref);
create index idx_notif_enviada_usuario_hash on notificacao_enviada (usuario_id, hash_evento);
create index idx_notif_enviada_usuario_categoria_data on notificacao_enviada (usuario_id, categoria, data_envio);

    alter table notificacoes_fechamento_cartao 
       add constraint uk_notif_cartao_data_fechamento unique (cartao_credito_id, data_fechamento_referencia);

    alter table orcamentos 
       add constraint uk_orcamento_usuario_categoria_mes_ano unique (usuario_id, categoria_id, mes, ano);

    alter table usuario_ai_config 
       add constraint UK_mcl00gvu8lutdg2t2yj94raac unique (usuario_id);

    alter table usuario_ai_config 
       add constraint UK_gjdgm44lbkfv4icyc6s9luq6s unique (evolution_instance_name);

    alter table usuario_configuracao_fiscal 
       add constraint UK_l2q1b8o21v7j0pttaobuh9dtm unique (usuario_id);

    alter table usuario_renda_config 
       add constraint UK_adfbw0lnbm0cuv6hru9eorp2v unique (usuario_id);

    alter table usuarios 
       add constraint UK_kfsp0s1tflm1cwlj8idhqsad0 unique (email);

    alter table usuarios 
       add constraint UK_69mcqeeg7pulu0ouige5ytybm unique (google_id);

    alter table usuarios 
       add constraint UK_m2dvbwfge291euvmk6vkkocao unique (username);

    alter table usuarios 
       add constraint UK_tgv9tbl7jkiukya411r5tertq unique (whatsapp_number);

    alter table usuarios_score 
       add constraint UK_h4xd1q7f780inl4y9mya487t0 unique (usuario_id);

    alter table whatsapp_lembrete_pendencia 
       add constraint uq_lembrete_usuario_transacao_tipo unique (usuario_id, transacao_id, tipo);

    alter table agendamentos_pagamentos 
       add constraint FKnepu5j4f2jfpowgalnf51hetr 
       foreign key (cartao_credito_id) 
       references cartoes_credito;

    alter table agendamentos_pagamentos 
       add constraint FKiqm0t26df1ke53h1eyd3xuyov 
       foreign key (categoria_id) 
       references categorias;

    alter table agendamentos_pagamentos 
       add constraint FKj3mooh3p3vl4lm4ohqowwiicc 
       foreign key (conta_debito_id) 
       references contas_bancarias;

    alter table agendamentos_pagamentos 
       add constraint FKp5l2e9xb749bjcua7seexeix0 
       foreign key (usuario_id) 
       references usuarios;

    alter table assinaturas_recorrentes 
       add constraint FKi1h0dg3t6347vn6nhkky4m5oi 
       foreign key (conta_debito_padrao_id) 
       references contas_bancarias;

    alter table assinaturas_recorrentes 
       add constraint FKkimhqbk8dr81p5nhtol6ebnj3 
       foreign key (usuario_id) 
       references usuarios;

    alter table cartoes_credito 
       add constraint FKqknuq6k2stohycjxrqpp0rpey 
       foreign key (usuario_id) 
       references usuarios;

    alter table categorias 
       add constraint FK7lnxm7e5lqkbw5qe0fy2pntl9 
       foreign key (usuario_id) 
       references usuarios;

    alter table compras_parceladas 
       add constraint FKdxqicloq8u7w4lis18xxu2fw8 
       foreign key (cartao_credito_id) 
       references cartoes_credito;

    alter table compras_parceladas 
       add constraint FK710eedo3cxbe3hr22gt9hmqc3 
       foreign key (categoria_id) 
       references categorias;

    alter table compras_parceladas 
       add constraint FK7i8r34g5oa1l9bgibejcs3a5d 
       foreign key (usuario_id) 
       references usuarios;

    alter table contas_bancarias 
       add constraint FKjhojqelvcv8pisv2g0su4qaf9 
       foreign key (usuario_id) 
       references usuarios;

    alter table contracheque_descontos 
       add constraint FKknmfr01k9ieeaxsjsrkqa881l 
       foreign key (contracheque_importado_id) 
       references contracheques_importados;

    alter table contracheques_importados 
       add constraint FKk3xvdqqkuhpphaxww10oi5tki 
       foreign key (usuario_id) 
       references usuarios;

    alter table debitos_internos 
       add constraint FKq8kx334q4r1h8a06214t4mhmr 
       foreign key (credor_usuario_id) 
       references usuarios;

    alter table debitos_internos 
       add constraint FKchh3xxlxigrxn4ix0ckr1kqat 
       foreign key (devedor_usuario_id) 
       references usuarios;

    alter table debitos_internos 
       add constraint FKcynl2mlklbqqx3y8mf7xm8xna 
       foreign key (grupo_familiar_id) 
       references grupos_familiares;

    alter table despesas_fixas 
       add constraint FK105jkpbhosjmg6mo92oqffpdu 
       foreign key (conta_bancaria_id) 
       references contas_bancarias;

    alter table despesas_fixas 
       add constraint FKfog2u2nd1tswh137ro4ks2ygy 
       foreign key (usuario_id) 
       references usuarios;

    alter table faturas 
       add constraint FKbo4apt4136o6wo9e4qv9ow8ri 
       foreign key (cartao_credito_id) 
       references cartoes_credito;

    alter table faturas 
       add constraint FKkq0wr9ryjqrpo7gmc59l7nn28 
       foreign key (usuario_id) 
       references usuarios;

    alter table grupo_familiar_membros 
       add constraint FKh7vbebtj7w0iyyer7bm3a7lax 
       foreign key (convidado_por_usuario_id) 
       references usuarios;

    alter table grupo_familiar_membros 
       add constraint FK555jnc8m4y6kk8ufx857lr2wd 
       foreign key (grupo_familiar_id) 
       references grupos_familiares;

    alter table grupo_familiar_membros 
       add constraint FKpwp8otw69fbhpj45adyyatkw6 
       foreign key (usuario_id) 
       references usuarios;

    alter table grupos_familiares 
       add constraint FK8iarytk01ycmgh29ue9ja0xm5 
       foreign key (criador_usuario_id) 
       references usuarios;

    alter table historico_score 
       add constraint FK1ub8qoubj1sabc5o1yn67hafm 
       foreign key (usuario_id) 
       references usuarios;

    alter table importacoes_fatura_cartao 
       add constraint FKsy38sm8ad0qsgk3w0ybpk3jwe 
       foreign key (cartao_credito_id) 
       references cartoes_credito;

    alter table importacoes_fatura_cartao 
       add constraint FKj38vqi9eewour1ou8nsf7am1i 
       foreign key (fatura_id) 
       references faturas;

    alter table importacoes_fatura_cartao 
       add constraint FKnx14vqgl5cla24213865voya6 
       foreign key (usuario_id) 
       references usuarios;

    alter table metas_financeiras 
       add constraint FKtd23m1qk8yq4p1ssmaiik6ccc 
       foreign key (usuario_id) 
       references usuarios;

    alter table notificacoes_fechamento_cartao 
       add constraint FK8sko8bgxbetbl52w6o3x3e2m7 
       foreign key (cartao_credito_id) 
       references cartoes_credito;

    alter table orcamentos 
       add constraint FKjmvbwtb5ele4ifedootj8k71d 
       foreign key (categoria_id) 
       references categorias;

    alter table orcamentos 
       add constraint FK9g1nr9c5b7i4jnvuhdw1wqjdi 
       foreign key (grupo_familiar_id) 
       references grupos_familiares;

    alter table orcamentos 
       add constraint FKqglev9uveaiftbxu0q958jky2 
       foreign key (usuario_id) 
       references usuarios;

    alter table parcelas 
       add constraint FKk4bwc6sxke3vxie9ehjigw2al 
       foreign key (compra_parcelada_id) 
       references compras_parceladas;

    alter table rendas 
       add constraint FKrmm3lcsnfa2clbyhq3x7yb9dd 
       foreign key (conta_destino_id) 
       references contas_bancarias;

    alter table rendas 
       add constraint FKfvk471wro35la1ituxgikoami 
       foreign key (usuario_id) 
       references usuarios;

    alter table sugestoes_contencao_jarvis 
       add constraint FKnd11olrma8ovtaey92b4hp3tu 
       foreign key (categoria_id) 
       references categorias;

    alter table sugestoes_contencao_jarvis 
       add constraint FKn8x2yq0syc63k1wga1lqgmjqx 
       foreign key (importacao_fatura_cartao_id) 
       references importacoes_fatura_cartao;

    alter table sugestoes_contencao_jarvis 
       add constraint FKnf2clviuxh7skxj653ri7iieh 
       foreign key (usuario_id) 
       references usuarios;

    alter table transacoes 
       add constraint FKpo0f1uru9p0eagc24edw76mw8 
       foreign key (categoria_id) 
       references categorias;

    alter table transacoes 
       add constraint FKogl5akonr01487yi4k7p5ht23 
       foreign key (conta_bancaria_id) 
       references contas_bancarias;

    alter table transacoes 
       add constraint FKanq20mx241e0i8u6uqlhymcy9 
       foreign key (fatura_id) 
       references faturas;

    alter table transacoes 
       add constraint FKkcpkacordh1eujovjxlgdmhcx 
       foreign key (usuario_id) 
       references usuarios;

    alter table transferencias_contas 
       add constraint FKlt3p2a3f45uigmmtt6rkxwj0a 
       foreign key (conta_destino_id) 
       references contas_bancarias;

    alter table transferencias_contas 
       add constraint FKq0a4hg7vk0wksub6x78cab20n 
       foreign key (conta_origem_id) 
       references contas_bancarias;

    alter table transferencias_contas 
       add constraint FKlxl7qjr591yust66l3adadopp 
       foreign key (usuario_id) 
       references usuarios;

    alter table usuario_ai_config 
       add constraint FKsw885eaxubec22977arkwks6u 
       foreign key (usuario_id) 
       references usuarios;

    alter table usuario_configuracao_fiscal 
       add constraint FKj96xk1po171vkay7rnxtfd9n4 
       foreign key (usuario_id) 
       references usuarios;

    alter table usuario_perfil_comportamental 
       add constraint FK7vpaot2y8mihwrgdyxhlvo8v5 
       foreign key (usuario_id) 
       references usuarios;

    alter table usuario_renda_config 
       add constraint FK6ks495cjq5dax08hhdfsmm28g 
       foreign key (conta_bancaria_id) 
       references contas_bancarias;

    alter table usuario_renda_config 
       add constraint FKq69nrf2kuu0iwydfb83cevmof 
       foreign key (usuario_id) 
       references usuarios;

    alter table usuarios_score 
       add constraint FK7nuh7pfm076487avdmbuvhl3q 
       foreign key (usuario_id) 
       references usuarios;
