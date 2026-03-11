import { apiClient } from './apiClient'

export interface CreateInvoiceResponse {
  invoiceLink: string
}

export const paymentsApi = {
  createInvoice: (clubId: string): Promise<CreateInvoiceResponse> =>
    apiClient.post<CreateInvoiceResponse>('/payments/create-invoice', { clubId }),
}

/**
 * Opens Telegram Stars invoice.
 * In dev/mock mode simulates successful payment.
 */
export function openTelegramInvoice(
  invoiceLink: string,
  onResult: (status: 'paid' | 'failed' | 'cancelled') => void,
): void {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const tg = (window as any).Telegram?.WebApp
  if (tg?.openInvoice) {
    tg.openInvoice(invoiceLink, (status: string) => {
      if (status === 'paid') {
        onResult('paid')
      } else if (status === 'cancelled') {
        onResult('cancelled')
      } else {
        onResult('failed')
      }
    })
  } else {
    // Mock mode: simulate paid
    setTimeout(() => onResult('paid'), 800)
  }
}
