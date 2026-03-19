import { ConfirmModal } from '@edifice.io/react';

type TicketCancelModalProps = {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: () => void;
};

export default function TicketCancelModal({
  isOpen,
  onClose,
  onConfirm,
}: TicketCancelModalProps) {
  return (
    <ConfirmModal
      body={
        <p>
          La création de la page sera perdue. Souhaitez-vous annuler la création
          ?
        </p>
      }
      isOpen={isOpen}
      header={<>Annuler la création</>}
      id="confirm-modal"
      okText="Annuler la création"
      koText="Retour à la création"
      onCancel={onClose}
      onSuccess={onConfirm}
    />
  );
}
