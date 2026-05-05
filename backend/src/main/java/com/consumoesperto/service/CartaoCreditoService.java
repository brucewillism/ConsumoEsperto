package com.consumoesperto.service;

import com.consumoesperto.dto.CartaoCreditoDTO;
import com.consumoesperto.model.CartaoCredito;
import com.consumoesperto.model.Usuario;
import com.consumoesperto.util.ApelidoNormalizador;
import com.consumoesperto.model.Fatura;
import com.consumoesperto.repository.CartaoCreditoRepository;
import com.consumoesperto.repository.FaturaRepository;
import com.consumoesperto.repository.TransacaoRepository;
import com.consumoesperto.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Serviço responsável por gerenciar operações relacionadas a cartões de crédito
 * 
 * Este serviço implementa a lógica de negócio para criação, busca, atualização
 * e exclusão de cartões de crédito. Também fornece métodos para cálculos
 * de limites e consultas específicas por usuário.
 * 
 * Funcionalidades principais:
 * - CRUD completo de cartões de crédito
 * - Validação de unicidade de números de cartão por usuário
 * - Cálculo de limites totais de crédito e disponível
 * - Controle de acesso por usuário
 * - Soft delete (desativação em vez de exclusão física)
 * 
 * @author ConsumoEsperto Team
 * @version 1.0
 */
@Service
@RequiredArgsConstructor // Lombok: gera construtor com campos final automaticamente
@Transactional // Todas as operações são transacionais para garantir consistência
@Slf4j
public class CartaoCreditoService {

    // Repositório para operações de persistência de cartões de crédito
    private final CartaoCreditoRepository cartaoCreditoRepository;

    private final FaturaRepository faturaRepository;

    private final TransacaoRepository transacaoRepository;
    
    // Repositório para validação e busca de usuários
    private final UsuarioRepository usuarioRepository;

    /**
     * Cria um novo cartão de crédito no sistema
     * 
     * Este método implementa o fluxo completo de criação de cartão:
     * 1. Valida se o usuário existe
     * 2. Verifica se o número do cartão já existe para o usuário
     * 3. Converte o DTO para entidade
     * 4. Associa o cartão ao usuário
     * 5. Persiste o cartão no banco de dados
     * 
     * @param cartaoCreditoDTO DTO com os dados do cartão a ser criado
     * @return CartaoCreditoDTO com os dados do cartão criado
     * @throws RuntimeException se o usuário não for encontrado ou se já existir cartão ativo com o mesmo número
     */
    public CartaoCreditoDTO criarCartaoCredito(CartaoCreditoDTO cartaoCreditoDTO) {
        Usuario usuario = usuarioRepository.findById(cartaoCreditoDTO.getUsuarioId())
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        Optional<CartaoCredito> existenteOpt = cartaoCreditoRepository.findByNumeroCartaoAndUsuarioId(
            cartaoCreditoDTO.getNumeroCartao(), cartaoCreditoDTO.getUsuarioId());

        if (existenteOpt.isPresent()) {
            CartaoCredito existente = existenteOpt.get();
            if (Boolean.TRUE.equals(existente.getAtivo())) {
                throw new RuntimeException("Cartão de crédito já existe para este usuário");
            }
            // Soft-delete na app: registo fica inativo e some da lista, mas bloqueava novo cadastro com o mesmo final.
            CartaoCreditoDTO patch = buscarPorId(existente.getId(), cartaoCreditoDTO.getUsuarioId());
            patch.setNome(cartaoCreditoDTO.getNome());
            patch.setBanco(cartaoCreditoDTO.getBanco());
            patch.setLimiteCredito(cartaoCreditoDTO.getLimiteCredito());
            patch.setLimiteDisponivel(cartaoCreditoDTO.getLimiteDisponivel());
            patch.setDiaVencimento(cartaoCreditoDTO.getDiaVencimento());
            patch.setAtivo(true);
            if (cartaoCreditoDTO.getTipoCartao() != null) {
                patch.setTipoCartao(cartaoCreditoDTO.getTipoCartao());
            }
            if (cartaoCreditoDTO.getCor() != null && !cartaoCreditoDTO.getCor().isBlank()) {
                patch.setCor(cartaoCreditoDTO.getCor());
            }
            if (cartaoCreditoDTO.getIcone() != null && !cartaoCreditoDTO.getIcone().isBlank()) {
                patch.setIcone(cartaoCreditoDTO.getIcone());
            }
            if (cartaoCreditoDTO.getDataVencimento() != null) {
                patch.setDataVencimento(cartaoCreditoDTO.getDataVencimento());
            }
            return atualizarCartaoCredito(existente.getId(), patch, cartaoCreditoDTO.getUsuarioId());
        }

        CartaoCredito cartaoCredito = converterParaEntidade(cartaoCreditoDTO);
        cartaoCredito.setUsuario(usuario);
        CartaoCredito cartaoSalvo = cartaoCreditoRepository.save(cartaoCredito);
        return converterParaDTO(cartaoSalvo);
    }

    /**
     * Indica se já existe registo com o mesmo número em estado inativo (ex.: apagado na app = soft delete).
     */
    public boolean isCartaoInativoComNumero(Long usuarioId, String numeroCartao) {
        if (numeroCartao == null || numeroCartao.isBlank()) {
            return false;
        }
        return cartaoCreditoRepository.findByNumeroCartaoAndUsuarioId(numeroCartao, usuarioId)
            .map(c -> !Boolean.TRUE.equals(c.getAtivo()))
            .orElse(false);
    }

    /**
     * Quando o WhatsApp envia CREATE_CARD para um número já cadastrado, aplica apelido/banco/dia de vencimento
     * e limites apenas quando o utilizador os indicou explicitamente (evita sobrescrever com default 1000).
     */
    public CartaoCreditoDTO mergeCartaoExistentePorNumero(CartaoCreditoDTO incoming, Long usuarioId,
            boolean limiteCreditoExplicito, boolean limiteDisponivelExplicito) {
        CartaoCredito existing = cartaoCreditoRepository
            .findByNumeroCartaoAndUsuarioId(incoming.getNumeroCartao(), usuarioId)
            .orElseThrow(() -> new RuntimeException("Cartão duplicado não localizado para atualização"));
        CartaoCreditoDTO patch = buscarPorId(existing.getId(), usuarioId);
        patch.setNome(incoming.getNome());
        patch.setBanco(incoming.getBanco());
        patch.setDiaVencimento(incoming.getDiaVencimento());
        if (limiteCreditoExplicito && incoming.getLimiteCredito() != null
            && incoming.getLimiteCredito().compareTo(BigDecimal.ZERO) > 0) {
            patch.setLimiteCredito(incoming.getLimiteCredito());
            if (!limiteDisponivelExplicito) {
                patch.setLimiteDisponivel(incoming.getLimiteCredito());
            }
        }
        if (limiteDisponivelExplicito && incoming.getLimiteDisponivel() != null
            && incoming.getLimiteDisponivel().compareTo(BigDecimal.ZERO) >= 0) {
            patch.setLimiteDisponivel(incoming.getLimiteDisponivel());
        }
        return atualizarCartaoCredito(existing.getId(), patch, usuarioId);
    }

    /**
     * Busca um cartão de crédito específico pelo seu ID
     * 
     * Método para recuperar cartões específicos por identificador.
     * Inclui validação de acesso por usuário.
     * 
     * @param id ID único do cartão de crédito a ser buscado
     * @param usuarioId ID do usuário solicitante (para validação de acesso)
     * @return CartaoCreditoDTO com os dados do cartão encontrado
     * @throws RuntimeException se o cartão não for encontrado ou não pertencer ao usuário
     */
    public CartaoCreditoDTO buscarPorId(Long id, Long usuarioId) {
        // Busca o cartão pelo ID e valida se pertence ao usuário
        CartaoCredito cartaoCredito = cartaoCreditoRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new RuntimeException("Cartão de crédito não encontrado"));
        return converterParaDTO(cartaoCredito);
    }

    /**
     * Lista todos os cartões de crédito ativos de um usuário específico
     * 
     * Método usado para exibir os cartões do usuário no dashboard
     * e outras telas do sistema. Retorna apenas cartões ativos.
     * 
     * @param usuarioId ID do usuário cujos cartões devem ser listados
     * @return Lista de CartaoCreditoDTO com os cartões ativos do usuário
     */
    public List<CartaoCreditoDTO> buscarPorUsuarioId(Long usuarioId) {
        List<CartaoCredito> cartoes = cartaoCreditoRepository.findByUsuarioIdAndAtivoTrue(usuarioId);
        return cartoes.stream()
                .map(this::converterParaDTO)
                .collect(Collectors.toList());
    }

    /**
     * Busca cartões de crédito por usuário (alias para buscarPorUsuarioId)
     * 
     * @param usuarioId ID do usuário
     * @return Lista de cartões do usuário
     */
    public List<CartaoCreditoDTO> buscarPorUsuario(Long usuarioId) {
        return buscarPorUsuarioId(usuarioId);
    }

    public Optional<CartaoCredito> buscarAtivoPorNomeOuBanco(Long usuarioId, String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        List<CartaoCredito> candidatos = cartaoCreditoRepository.findAtivosByUsuarioIdAndNomeOrBancoLike(usuarioId, token.trim());
        return candidatos.stream().findFirst();
    }

    public List<CartaoCredito> buscarAtivosPorNomeOuBancoAproximado(Long usuarioId, String termo) {
        if (termo == null || termo.isBlank()) {
            return List.of();
        }
        return cartaoCreditoRepository.findAtivosByUsuarioIdAndNomeOrBancoLike(usuarioId, termo.trim()).stream()
            .sorted(Comparator.comparing(CartaoCredito::getLimiteDisponivel, Comparator.nullsFirst(BigDecimal::compareTo)).reversed())
            .collect(Collectors.toList());
    }

    /**
     * Resolve cartões ativos por apelido (nome) ou banco, com normalização insensível a maiúsculas e acentos.
     * Lista vazia: nenhum; um elemento: match único; vários: ambíguo (usuário deve refinar).
     */
    public List<CartaoCredito> encontrarAtivosPorApelidoNormalizado(Long usuarioId, String apelidoOuBanco) {
        if (apelidoOuBanco == null || apelidoOuBanco.isBlank()) {
            return List.of();
        }
        String token = ApelidoNormalizador.normalizar(apelidoOuBanco);
        if (token.length() < 2) {
            return List.of();
        }
        List<CartaoCredito> todos = cartaoCreditoRepository.findByUsuarioIdAndAtivoTrue(usuarioId);
        List<CartaoCredito> exatosNome = todos.stream()
            .filter(c -> ApelidoNormalizador.normalizar(c.getNome()).equals(token))
            .collect(Collectors.toList());
        if (exatosNome.size() == 1) {
            return exatosNome;
        }
        if (exatosNome.size() > 1) {
            return exatosNome;
        }
        List<CartaoCredito> exatosBanco = todos.stream()
            .filter(c -> ApelidoNormalizador.normalizar(c.getBanco()).equals(token))
            .collect(Collectors.toList());
        if (exatosBanco.size() == 1) {
            return exatosBanco;
        }
        if (exatosBanco.size() > 1) {
            return exatosBanco;
        }
        List<CartaoCredito> parcial = todos.stream()
            .filter(c -> {
                String nn = ApelidoNormalizador.normalizar(c.getNome());
                String bb = ApelidoNormalizador.normalizar(c.getBanco());
                return nn.contains(token) || bb.contains(token);
            })
            .collect(Collectors.toList());
        if (parcial.size() == 1) {
            return parcial;
        }
        if (parcial.size() > 1) {
            return parcial;
        }
        return List.of();
    }

    /**
     * Atualiza limite(s) e/ou apelido de um cartão já validado como pertencente ao {@code usuarioId}.
     */
    public CartaoCreditoDTO atualizarConfigPorCartaoId(Long usuarioId, Long cartaoId,
            BigDecimal novoLimiteCredito, BigDecimal novoLimiteDisponivel, String novoApelido) {
        return atualizarConfigPorCartaoId(usuarioId, cartaoId, novoLimiteCredito, novoLimiteDisponivel, novoApelido,
            null, null, null);
    }

    public CartaoCreditoDTO atualizarConfigPorCartaoId(Long usuarioId, Long cartaoId,
            BigDecimal novoLimiteCredito, BigDecimal novoLimiteDisponivel, String novoApelido,
            String novoBanco, String novaCor, String novoIcone) {
        CartaoCredito c = cartaoCreditoRepository.findByIdAndUsuarioId(cartaoId, usuarioId)
            .orElseThrow(() -> new RuntimeException("Cartão de crédito não encontrado"));

        if (novoLimiteCredito != null || novoLimiteDisponivel != null) {
            log.info("[ACCOUNT-LOG] Atualizando limite do cartão {} (id={}) para o usuário {}...",
                c.getNome(), c.getId(), usuarioId);
        } else {
            log.info("[ACCOUNT-LOG] Atualizando config do cartão {} (id={}) para o usuário {}...",
                c.getNome(), c.getId(), usuarioId);
        }

        boolean changed = false;
        if (novoApelido != null && !novoApelido.isBlank()) {
            String nome = novoApelido.replaceAll("[^\\p{L}0-9\\s\\-]", "").trim();
            if (!nome.isBlank()) {
                c.setNome(nome);
                changed = true;
            }
        }
        if (novoBanco != null && !novoBanco.isBlank()) {
            c.setBanco(novoBanco.replaceAll("[^\\p{L}0-9\\s\\-]", "").trim());
            changed = true;
        }
        if (novaCor != null && !novaCor.isBlank()) {
            c.setCor(novaCor.trim());
            changed = true;
        }
        if (novoIcone != null && !novoIcone.isBlank()) {
            c.setIcone(novoIcone.trim());
            changed = true;
        }
        if (novoLimiteCredito != null && novoLimiteCredito.compareTo(BigDecimal.ZERO) > 0) {
            c.setLimiteCredito(novoLimiteCredito);
            if (c.getLimiteDisponivel() == null || c.getLimiteDisponivel().compareTo(novoLimiteCredito) > 0) {
                c.setLimiteDisponivel(novoLimiteCredito);
            }
            changed = true;
        }
        if (novoLimiteDisponivel != null && novoLimiteDisponivel.compareTo(BigDecimal.ZERO) >= 0) {
            BigDecimal teto = c.getLimiteCredito() != null ? c.getLimiteCredito() : novoLimiteDisponivel;
            c.setLimiteDisponivel(novoLimiteDisponivel.min(teto));
            changed = true;
        }
        if (!changed) {
            throw new RuntimeException("Nenhuma alteração válida para o cartão");
        }
        CartaoCredito salvo = cartaoCreditoRepository.save(c);
        return converterParaDTO(salvo);
    }

    /**
     * Atualiza os dados de um cartão de crédito existente
     * 
     * Este método permite modificar informações do cartão como:
     * - Nome do cartão
     * - Banco emissor
     * - Limite de crédito
     * - Limite disponível
     * - Data de vencimento
     * - Tipo do cartão
     * - Status ativo/inativo
     * 
     * Inclui validação de acesso por usuário.
     * 
     * @param id ID do cartão a ser atualizado
     * @param cartaoCreditoDTO DTO com os novos dados do cartão
     * @param usuarioId ID do usuário solicitante (para validação de acesso)
     * @return CartaoCreditoDTO com os dados atualizados
     * @throws RuntimeException se o cartão não for encontrado ou não pertencer ao usuário
     */
    public CartaoCreditoDTO atualizarCartaoCredito(Long id, CartaoCreditoDTO cartaoCreditoDTO, Long usuarioId) {
        // Verifica se o cartão existe e pertence ao usuário antes de tentar atualizar
        CartaoCredito cartaoExistente = cartaoCreditoRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new RuntimeException("Cartão de crédito não encontrado"));

        // Atualiza todos os campos do cartão com os novos valores
        cartaoExistente.setNome(cartaoCreditoDTO.getNome());
        cartaoExistente.setBanco(cartaoCreditoDTO.getBanco());
        cartaoExistente.setLimiteCredito(cartaoCreditoDTO.getLimiteCredito());
        cartaoExistente.setLimiteDisponivel(cartaoCreditoDTO.getLimiteDisponivel());
        cartaoExistente.setDiaVencimento(cartaoCreditoDTO.getDiaVencimento());
        cartaoExistente.setDataVencimento(cartaoCreditoDTO.getDataVencimento());
        cartaoExistente.setTipoCartao(cartaoCreditoDTO.getTipoCartao());
        cartaoExistente.setAtivo(cartaoCreditoDTO.getAtivo());
        cartaoExistente.setCor(cartaoCreditoDTO.getCor());
        cartaoExistente.setIcone(cartaoCreditoDTO.getIcone());

        // Persiste as alterações no banco de dados
        CartaoCredito cartaoAtualizado = cartaoCreditoRepository.save(cartaoExistente);
        return converterParaDTO(cartaoAtualizado);
    }

    /**
     * Desativa um cartão de crédito (soft delete)
     * 
     * ATENÇÃO: Esta operação não remove o cartão fisicamente do banco,
     * apenas o marca como inativo. Isso preserva o histórico de transações
     * e faturas associadas ao cartão.
     * 
     * Inclui validação de acesso por usuário.
     * 
     * @param id ID do cartão a ser desativado
     * @param usuarioId ID do usuário solicitante (para validação de acesso)
     * @throws RuntimeException se o cartão não for encontrado ou não pertencer ao usuário
     */
    public void deletarCartaoCredito(Long id, Long usuarioId) {
        // Verifica se o cartão existe e pertence ao usuário antes de tentar desativar
        CartaoCredito cartaoCredito = cartaoCreditoRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new RuntimeException("Cartão de crédito não encontrado"));
        
        // Soft delete: marca o cartão como inativo em vez de removê-lo fisicamente
        cartaoCredito.setAtivo(false);
        cartaoCreditoRepository.save(cartaoCredito);
    }

    public long contarFaturasDoCartao(Long cartaoId, Long usuarioId) {
        cartaoCreditoRepository.findByIdAndUsuarioId(cartaoId, usuarioId)
            .orElseThrow(() -> new RuntimeException("Cartão de crédito não encontrado"));
        return faturaRepository.countByCartaoCreditoId(cartaoId);
    }

    /**
     * Move todas as faturas de um cartão para outro (mesmo utilizador).
     */
    @Transactional
    public void reatribuirFaturasParaOutroCartao(Long cartaoOrigemId, Long cartaoDestinoId, Long usuarioId) {
        if (Objects.equals(cartaoOrigemId, cartaoDestinoId)) {
            throw new IllegalArgumentException("Escolhe um cartão diferente do original.");
        }
        cartaoCreditoRepository.findByIdAndUsuarioId(cartaoOrigemId, usuarioId)
            .orElseThrow(() -> new RuntimeException("Cartão de origem não encontrado"));
        CartaoCredito destino = cartaoCreditoRepository.findByIdAndUsuarioId(cartaoDestinoId, usuarioId)
            .orElseThrow(() -> new RuntimeException("Cartão de destino não encontrado"));
        if (!Boolean.TRUE.equals(destino.getAtivo())) {
            throw new IllegalStateException("O cartão de destino está inativo.");
        }
        List<Fatura> faturas = faturaRepository.findByCartaoCreditoId(cartaoOrigemId);
        for (Fatura f : faturas) {
            f.setCartaoCredito(destino);
        }
        faturaRepository.saveAll(faturas);
    }

    @Transactional
    public void apagarFaturasDoCartao(Long cartaoId, Long usuarioId) {
        cartaoCreditoRepository.findByIdAndUsuarioId(cartaoId, usuarioId)
            .orElseThrow(() -> new RuntimeException("Cartão não encontrado"));
        List<Fatura> faturas = faturaRepository.findByCartaoCreditoId(cartaoId);
        faturaRepository.deleteAll(faturas);
    }

    /**
     * Calcula o limite total de crédito de um usuário
     * 
     * Método usado para cálculos financeiros e relatórios.
     * Soma o limite de crédito de todos os cartões ativos do usuário.
     * Retorna BigDecimal para precisão em cálculos monetários.
     * 
     * @param usuarioId ID do usuário para o qual calcular o total
     * @return BigDecimal com o limite total de crédito do usuário
     */
    public BigDecimal getTotalLimiteCredito(Long usuarioId) {
        // Busca o total do limite de crédito de todos os cartões ativos do usuário
        Double total = cartaoCreditoRepository.getTotalCreditLimitByUsuarioId(usuarioId);
        
        // Converte para BigDecimal ou retorna zero se não houver cartões
        return total != null ? BigDecimal.valueOf(total) : BigDecimal.ZERO;
    }

    /**
     * Calcula o limite total disponível de um usuário
     * 
     * Método usado para cálculos financeiros e relatórios.
     * Soma o limite disponível de todos os cartões ativos do usuário.
     * Retorna BigDecimal para precisão em cálculos monetários.
     * 
     * @param usuarioId ID do usuário para o qual calcular o total
     * @return BigDecimal com o limite total disponível do usuário
     */
    public BigDecimal getTotalLimiteDisponivel(Long usuarioId) {
        List<CartaoCredito> cartoes = cartaoCreditoRepository.findByUsuarioIdAndAtivoTrue(usuarioId);
        BigDecimal total = BigDecimal.ZERO;
        for (CartaoCredito c : cartoes) {
            if (c.getId() == null) {
                continue;
            }
            BigDecimal lim = c.getLimiteCredito() != null ? c.getLimiteCredito() : BigDecimal.ZERO;
            BigDecimal util = calcularLimiteUtilizadoAberto(c.getId());
            BigDecimal disp = lim.subtract(util).max(BigDecimal.ZERO);
            total = total.add(disp);
        }
        return total;
    }

    /**
     * Soma das despesas confirmadas nas faturas ABERTA/PARCIAL do cartão; se zero, usa o acumulado legado nas faturas.
     */
    public BigDecimal calcularLimiteUtilizadoAberto(Long cartaoId) {
        BigDecimal a = transacaoRepository.sumDespesaConfirmadaFaturasNaoPagasPorCartaoId(cartaoId);
        if (a != null && a.compareTo(BigDecimal.ZERO) > 0) {
            return a;
        }
        BigDecimal b = faturaRepository.sumValorFaturasAbertasPorCartaoId(cartaoId);
        return b != null ? b : BigDecimal.ZERO;
    }

    /**
     * Remove todos os cartões de crédito de um banco específico para um usuário
     * 
     * @param usuarioId ID do usuário
     * @param banco Nome do banco
     */
    @Transactional
    public void removerPorBanco(Long usuarioId, String banco) {
        List<CartaoCredito> cartoes = cartaoCreditoRepository.findByUsuarioId(usuarioId);
        
        for (CartaoCredito cartao : cartoes) {
            if (cartao.getBanco().equals(banco)) {
                cartao.setAtivo(false);
                cartaoCreditoRepository.save(cartao);
            }
        }
    }

    /**
     * Converte um CartaoCreditoDTO para entidade CartaoCredito
     * 
     * Este método é responsável por:
     * - Mapear dados do DTO para a entidade
     * - Preparar a entidade para persistência
     * 
     * @param dto CartaoCreditoDTO a ser convertido
     * @return Entidade CartaoCredito com os dados do DTO
     */
    private CartaoCredito converterParaEntidade(CartaoCreditoDTO dto) {
        CartaoCredito cartaoCredito = new CartaoCredito();
        cartaoCredito.setId(dto.getId());
        cartaoCredito.setNome(dto.getNome());
        cartaoCredito.setBanco(dto.getBanco());
        cartaoCredito.setNumeroCartao(dto.getNumeroCartao());
        cartaoCredito.setLimiteCredito(dto.getLimiteCredito());
        cartaoCredito.setLimiteDisponivel(dto.getLimiteDisponivel());
        cartaoCredito.setDiaVencimento(dto.getDiaVencimento());
        cartaoCredito.setDataVencimento(dto.getDataVencimento());
        cartaoCredito.setTipoCartao(dto.getTipoCartao());
        cartaoCredito.setAtivo(dto.getAtivo());
        cartaoCredito.setCor(dto.getCor());
        cartaoCredito.setIcone(dto.getIcone());
        return cartaoCredito;
    }

    /**
     * Converte uma entidade CartaoCredito para CartaoCreditoDTO
     * 
     * Este método é responsável por:
     * - Mapear dados da entidade para o DTO
     * - Incluir informações do usuário associado
     * - Garantir que dados sensíveis não sejam expostos
     * 
     * @param cartaoCredito Entidade CartaoCredito a ser convertida
     * @return CartaoCreditoDTO com todos os dados necessários para exibição
     */
    private CartaoCreditoDTO converterParaDTO(CartaoCredito cartaoCredito) {
        CartaoCreditoDTO dto = new CartaoCreditoDTO();
        dto.setId(cartaoCredito.getId());
        dto.setNome(cartaoCredito.getNome());
        dto.setBanco(cartaoCredito.getBanco());
        dto.setNumeroCartao(cartaoCredito.getNumeroCartao());
        dto.setLimiteCredito(cartaoCredito.getLimiteCredito());
        dto.setLimiteDisponivel(cartaoCredito.getLimiteDisponivel());
        dto.setDiaVencimento(cartaoCredito.getDiaVencimento());
        dto.setDataVencimento(cartaoCredito.getDataVencimento());
        dto.setTipoCartao(cartaoCredito.getTipoCartao());
        dto.setAtivo(cartaoCredito.getAtivo());
        dto.setCor(cartaoCredito.getCor());
        dto.setIcone(cartaoCredito.getIcone());
        
        // Inclui informações do usuário associado
        dto.setUsuarioId(cartaoCredito.getUsuario().getId());
        dto.setDataCriacao(cartaoCredito.getDataCriacao());
        dto.setDataAtualizacao(cartaoCredito.getDataAtualizacao());

        BigDecimal limite = cartaoCredito.getLimiteCredito() != null ? cartaoCredito.getLimiteCredito() : BigDecimal.ZERO;
        BigDecimal utilizado = cartaoCredito.getId() != null
            ? calcularLimiteUtilizadoAberto(cartaoCredito.getId())
            : BigDecimal.ZERO;
        dto.setLimiteUtilizado(utilizado);
        dto.setLimiteDisponivel(limite.subtract(utilizado).max(BigDecimal.ZERO));
        return dto;
    }
}
