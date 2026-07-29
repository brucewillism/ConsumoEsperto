import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { AppComponent } from './app.component';
import { AuthService } from './services/auth.service';
import { LoadingService } from './services/loading.service';
import { NotificacaoInboxService } from './services/notificacao-inbox.service';
import { ScoreService } from './services/score.service';

describe('AppComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        { provide: AuthService, useValue: { currentUser$: of(null) } },
        {
          provide: LoadingService,
          useValue: {
            shellOverlay$: of({ active: false, message: '' }),
            isAuthFlowActive: () => false,
            endAuthFlow: () => undefined,
          },
        },
        { provide: NotificacaoInboxService, useValue: { listar: () => of([]) } },
        { provide: ScoreService, useValue: { obter: () => of(null) } },
      ],
    }).compileComponents();
  });

  it('cria a aplicação', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
    fixture.detectChanges();
    expect(app.isAuthenticated).toBeFalse();
  });
});
