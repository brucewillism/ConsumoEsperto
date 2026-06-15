import { MatDialogModule } from '@angular/material/dialog';
import { CeOverlayScrollableDirective } from './ce-overlay-scrollable.directive';

/** MatDialog + áreas roláveis dentro de overlays (cdkScrollable). */
export const CE_DIALOG_IMPORTS = [MatDialogModule, CeOverlayScrollableDirective] as const;
