import { Injectable } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';

@Injectable({
  providedIn: 'root'
})
export class ToastService {
  constructor(private snackBar: MatSnackBar) {}

  success(message: string): void {
    this.open(message, ['success-snackbar']);
  }

  error(message: string): void {
    this.open(message, ['error-snackbar']);
  }

  warning(message: string): void {
    this.open(message, ['warning-snackbar']);
  }

  info(message: string): void {
    this.open(message, ['info-snackbar']);
  }

  private open(message: string, panelClass: string[]): void {
    this.snackBar.open(message, 'Fechar', {
      duration: 4000,
      horizontalPosition: 'right',
      verticalPosition: 'top',
      panelClass
    });
  }
}
