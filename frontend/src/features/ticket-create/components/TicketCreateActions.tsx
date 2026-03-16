import { Button, ConfirmModal, Flex } from '@edifice.io/react';
import { IconSave } from '@edifice.io/react/icons';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useCanEscalate } from '~/hooks/useCanEscalate';

type TicketCreateActionsProps = {
  isValid: boolean;
  isPending: boolean;
  onSubmit: () => void;
  onSubmitAndEscalate: () => void;
};

export default function TicketCreateActions({
  isValid,
  isPending,
  onSubmit,
  onSubmitAndEscalate,
}: TicketCreateActionsProps) {
  const [cancelModalVisible, setCancelModalVisible] = useState(false);
  const navigate = useNavigate();
  const canEscalate = useCanEscalate();

  return (
    <>
      <Flex direction="row" gap="8" justify="end">
        <Button
          type="button"
          variant="ghost"
          disabled={isPending}
          onClick={() => setCancelModalVisible(true)}
        >
          Annuler
        </Button>

        <Button
          type="button"
          variant="outline"
          leftIcon={<IconSave />}
          disabled={!isValid || isPending}
          onClick={onSubmit}
        >
          Créer
        </Button>

        {canEscalate && (
          <Button
            type="button"
            disabled={!isValid || isPending}
            onClick={onSubmitAndEscalate}
          >
            Créer et transmettre au support ENT
          </Button>
        )}
      </Flex>

      <ConfirmModal
        body={
          <p>
            La création de la page sera perdue. Souhaitez-vous annuler la
            création ?
          </p>
        }
        isOpen={cancelModalVisible}
        header={<>Annuler la création</>}
        id="confirm-modal"
        okText="Annuler la création"
        koText="Retour à la création"
        onCancel={() => setCancelModalVisible(false)}
        onSuccess={() => {
          setCancelModalVisible(false);
          navigate('/');
        }}
      />
    </>
  );
}
