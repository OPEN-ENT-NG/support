import { Button, Flex } from '@edifice.io/react';
import { IconSave } from '@edifice.io/react/icons';
import { useCanEscalate } from '~/hooks/useCanEscalate';
import { useI18n } from '~/hooks/usei18n';

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
  const { t } = useI18n();
  const canEscalate = useCanEscalate();

  return (
    <Flex direction="row" gap="8" justify="end">
      <Button
        type="button"
        variant="ghost"
        disabled={isPending}
        onClick={onCancelClick}
      >
        {t('support.ticket.cancel')}
      </Button>

      <Button
        type="button"
        variant="outline"
        leftIcon={<IconSave />}
        disabled={!isValid || isPending}
        onClick={onSubmit}
      >
        {t('support.ticket.action.create')}
      </Button>

      {canEscalate && (
        <Button
          type="button"
          disabled={!isValid || isPending}
          onClick={onSubmitAndEscalate}
        >
          {t('support.ticket.action.create.and.escalate')}
        </Button>
      )}
    </Flex>
  );
}
