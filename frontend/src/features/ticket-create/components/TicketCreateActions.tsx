import { Button, Flex } from '@edifice.io/react';
import { IconSave } from '@edifice.io/react/icons';
import { useCanEscalate } from '~/hooks/useCanEscalate';

type TicketCreateActionsProps = {
  isValid: boolean;
  isPending: boolean;
  onSubmit: () => void;
  onSubmitAndEscalate: () => void;
  onCancelClick: () => void;
};

export default function TicketCreateActions({
  isValid,
  isPending,
  onSubmit,
  onSubmitAndEscalate,
  onCancelClick,
}: TicketCreateActionsProps) {
  const canEscalate = useCanEscalate();

  return (
    <Flex direction="row" gap="8" justify="end">
      <Button
        type="button"
        variant="ghost"
        disabled={isPending}
        onClick={onCancelClick}
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
  );
}
