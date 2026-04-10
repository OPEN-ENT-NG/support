import { ConfirmModal } from '@edifice.io/react';
import { useI18n } from '~/hooks/usei18n';

type TicketCancelModalProps = {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: () => void;
};

export function TicketCancelModal({
  isOpen,
  onClose,
  onConfirm,
}: TicketCancelModalProps) {
  const { t } = useI18n();

  return (
    <ConfirmModal
      body={<p>{t('support.ticket.cancel.modal.body')}</p>}
      isOpen={isOpen}
      header={<>{t('support.ticket.cancel.modal.title')}</>}
      id="confirm-modal"
      okText={t('support.ticket.cancel.modal.confirm')}
      koText={t('support.ticket.cancel.modal.back')}
      onCancel={onClose}
      onSuccess={onConfirm}
    />
  );
}
