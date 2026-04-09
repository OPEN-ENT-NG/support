import { Button, EmptyScreen, Flex } from '@edifice.io/react';
import { IconPlus } from '@edifice.io/react/icons';

import illuEmptyAdminThreads from '@images/emptyscreen/illu-actualites.svg';
import { useNavigate } from 'react-router-dom';
import { useI18n } from '~/hooks/usei18n';

export default function EmptyTicketsTable() {
  const navigate = useNavigate();
  const { t } = useI18n();

  const handleCreateTicket = () => {
    navigate('/tickets/new');
  };

  return (
    <Flex direction="column" gap="24" justify="center" align="center">
      <EmptyScreen
        imageSrc={illuEmptyAdminThreads}
        imageAlt="No tickets"
        title={t('support.ticket.empty.title')}
        text={t('support.ticket.empty.subtitle')}
      />
      <Button
        color="primary"
        variant="filled"
        size="md"
        onClick={handleCreateTicket}
      >
        <IconPlus /> {t('support.ticket.empty.create')}
      </Button>
    </Flex>
  );
}
