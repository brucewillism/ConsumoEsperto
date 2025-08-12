# 🎨 Frontend - ConsumoEsperto

Interface moderna desenvolvida em **Angular 17** com Angular Material e gráficos interativos.

## 🏗️ Arquitetura

### **Stack Tecnológica**
- **Framework**: Angular 17.2.0
- **Node.js**: 20.11.0 (LTS)
- **Package Manager**: npm 10.2.4
- **UI Framework**: Angular Material 19.2.19
- **Gráficos**: Chart.js 4.5.0 + ng2-charts 8.0.0
- **Estado**: RxJS 7.8.0
- **Build**: Angular CLI 19.2.15

### **Estrutura do Projeto**
```
frontend/
├── src/app/
│   ├── components/           # Componentes reutilizáveis
│   ├── pages/               # Páginas da aplicação
│   │   ├── dashboard/       # Dashboard principal
│   │   ├── transacoes/      # Gestão de transações
│   │   ├── cartoes/         # Gestão de cartões
│   │   ├── faturas/         # Gestão de faturas
│   │   ├── relatorios/      # Relatórios financeiros
│   │   ├── simulacoes/      # Simulações de compras
│   │   └── login/           # Autenticação
│   ├── services/            # Serviços HTTP e lógica
│   ├── models/              # Interfaces TypeScript
│   ├── guards/              # Guards de rota
│   └── app.routes.ts        # Configuração de rotas
├── src/environments/        # Configurações por ambiente
├── src/styles/              # Estilos globais
├── angular.json             # Configuração do Angular
└── package.json             # Dependências npm
```

## 🚀 Execução

### **Setup Automático (Recomendado)**
```bash
# Na raiz do projeto:
setup-completo-projeto.bat
```

### **Execução Manual**
```bash
cd frontend

# Instalar dependências
npm install

# Executar em desenvolvimento
npm start
# ou
ng serve

# Build para produção
npm run build
# ou
ng build --configuration production
```

## 🎨 Componentes Principais

### **Dashboard**
- **Localização**: `src/app/pages/dashboard/`
- **Funcionalidades**: 
  - Resumo financeiro
  - Gráficos de receitas/despesas
  - Saldo atual
  - Transações recentes

### **Transações**
- **Localização**: `src/app/pages/transacoes/`
- **Funcionalidades**:
  - Lista de transações
  - Filtros por categoria/data
  - Adicionar/editar transações
  - Categorização automática

### **Cartões de Crédito**
- **Localização**: `src/app/pages/cartoes/`
- **Funcionalidades**:
  - Gestão de cartões
  - Limites e vencimentos
  - Histórico de faturas
  - Alertas de vencimento

### **Faturas**
- **Localização**: `src/app/pages/faturas/`
- **Funcionalidades**:
  - Visualização de faturas
  - Pagamentos
  - Histórico
  - Alertas

### **Relatórios**
- **Localização**: `src/app/pages/relatorios/`
- **Funcionalidades**:
  - Relatórios por período
  - Análise por categoria
  - Comparativos mensais
  - Exportação de dados

## 🔐 Autenticação

### **Sistema de Login**
- **Métodos**: Email/Senha + Google OAuth2
- **JWT**: Token armazenado no localStorage
- **Guards**: Proteção de rotas autenticadas
- **Interceptors**: Adição automática de token nas requisições

### **Guards de Rota**
```typescript
// src/app/guards/auth.guard.ts
@Injectable({
  providedIn: 'root'
})
export class AuthGuard {
  canActivate(): boolean {
    return this.authService.isAuthenticated();
  }
}
```

## 🌐 Integração com Backend

### **Serviços HTTP**
- **AuthService**: Autenticação e gerenciamento de tokens
- **TransacaoService**: CRUD de transações
- **CartaoCreditoService**: Gestão de cartões
- **FaturaService**: Gestão de faturas
- **RelatorioService**: Geração de relatórios
- **BankApiService**: Integração com APIs bancárias

### **Interceptors**
```typescript
// src/app/interceptors/auth.interceptor.ts
@Injectable()
export class AuthInterceptor implements HttpInterceptor {
  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    const token = this.authService.getToken();
    if (token) {
      req = req.clone({
        setHeaders: { Authorization: `Bearer ${token}` }
      });
    }
    return next.handle(req);
  }
}
```

## 📊 Gráficos e Visualizações

### **Chart.js + ng2-charts**
- **Tipos**: Linha, Barra, Pizza, Doughnut
- **Responsivo**: Adaptação automática ao tamanho da tela
- **Interativo**: Tooltips e cliques nos elementos
- **Temas**: Suporte a temas claro/escuro

### **Exemplo de Gráfico**
```typescript
// Componente de gráfico
export class DashboardComponent {
  public chartData = [
    { data: [65, 59, 80, 81, 56, 55, 40], label: 'Receitas' },
    { data: [28, 48, 40, 19, 86, 27, 90], label: 'Despesas' }
  ];
  
  public chartLabels = ['Jan', 'Fev', 'Mar', 'Abr', 'Mai', 'Jun', 'Jul'];
  public chartOptions = {
    responsive: true,
    maintainAspectRatio: false
  };
}
```

## 🎨 Estilos e Temas

### **Angular Material**
- **Componentes**: Botões, inputs, cards, tabelas, modais
- **Tema**: Material Design 3
- **Cores**: Paleta personalizada para finanças
- **Responsivo**: Mobile-first design

### **CSS Customizado**
- **Variáveis CSS**: Cores e espaçamentos consistentes
- **Grid System**: Layout flexível e responsivo
- **Animações**: Transições suaves entre estados
- **Dark Mode**: Suporte a tema escuro

## 📱 Responsividade

### **Breakpoints**
```scss
// Mobile First
$mobile: 576px;
$tablet: 768px;
$desktop: 992px;
$large: 1200px;

// Media Queries
@media (min-width: $tablet) {
  .container {
    max-width: 720px;
  }
}
```

### **Componentes Adaptativos**
- **Tabelas**: Scroll horizontal em mobile
- **Gráficos**: Redimensionamento automático
- **Navegação**: Menu hambúrguer em mobile
- **Formulários**: Layout otimizado para touch

## 🧪 Testes

### **Executar Testes**
```bash
# Testes unitários
npm test

# Testes E2E
npm run e2e

# Cobertura de testes
npm run test:coverage
```

### **Ferramentas de Teste**
- **Jasmine**: Framework de testes
- **Karma**: Runner de testes
- **Protractor**: Testes E2E (legado)
- **Cypress**: Testes E2E (recomendado)

## 🚀 Build e Deploy

### **Configurações de Build**
```json
// angular.json
{
  "configurations": {
    "production": {
      "optimization": true,
      "outputHashing": "all",
      "sourceMap": false,
      "namedChunks": false,
      "aot": true,
      "extractLicenses": true,
      "vendorChunk": false,
      "buildOptimizer": true
    }
  }
}
```

### **Variáveis de Ambiente**
```typescript
// src/environments/environment.ts
export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080/api',
  ngrokUrl: 'https://your-tunnel.ngrok-free.app'
};

// src/environments/environment.prod.ts
export const environment = {
  production: true,
  apiUrl: 'https://api.consumoesperto.com/api',
  ngrokUrl: 'https://api.consumoesperto.com'
};
```

## 🐳 Docker

### **Dockerfile**
```dockerfile
# Multi-stage build
FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci --only=production

FROM nginx:alpine
COPY --from=builder /app/dist/frontend /usr/share/nginx/html
COPY nginx.conf /etc/nginx/nginx.conf
EXPOSE 80
```

### **Executar Container**
```bash
# Build da imagem
docker build -t consumo-esperto-frontend .

# Executar
docker run -p 80:80 consumo-esperto-frontend
```

## 🔧 Configuração de Desenvolvimento

### **VS Code Extensions Recomendadas**
- **Angular Language Service**: IntelliSense para Angular
- **ESLint**: Linting de código
- **Prettier**: Formatação automática
- **Angular Snippets**: Snippets para Angular
- **Material Icon Theme**: Ícones para Angular Material

### **Configurações do VS Code**
```json
// .vscode/settings.json
{
  "typescript.preferences.importModuleSpecifier": "relative",
  "editor.formatOnSave": true,
  "editor.codeActionsOnSave": {
    "source.fixAll.eslint": true
  }
}
```

## 🚨 Solução de Problemas

### **Erro de Dependências**
```bash
# Limpar cache npm
npm cache clean --force

# Deletar node_modules
rm -rf node_modules package-lock.json

# Reinstalar
npm install
```

### **Erro de Compilação**
```bash
# Verificar versão do Node.js
node --version

# Verificar versão do Angular CLI
ng version

# Limpar cache do Angular
ng cache clean
```

### **Problemas de CORS**
```bash
# Verificar se o backend está rodando
# Verificar configurações CORS no backend
# Verificar se as URLs estão corretas
```

## 🔄 Atualizações

### **Angular**
```bash
# Verificar versão atual
ng version

# Atualizar Angular CLI
npm install -g @angular/cli@latest

# Atualizar projeto
ng update @angular/core @angular/cli
```

### **Dependências**
```bash
# Verificar atualizações
npm outdated

# Atualizar dependências
npm update

# Atualizar para versões mais recentes
npm install package@latest
```

## 📚 Recursos Adicionais

### **Documentação Oficial**
- [Angular Documentation](https://angular.io/docs)
- [Angular Material](https://material.angular.io/)
- [Chart.js](https://www.chartjs.org/)
- [ng2-charts](https://valor-software.com/ng2-charts/)

### **Tutoriais e Exemplos**
- [Angular Tour of Heroes](https://angular.io/tutorial)
- [Angular Material Examples](https://material.angular.io/components/categories)
- [Chart.js Examples](https://www.chartjs.org/samples/)

---

**🎯 Para desenvolvimento, use sempre o setup automático: `setup-completo-projeto.bat`**
