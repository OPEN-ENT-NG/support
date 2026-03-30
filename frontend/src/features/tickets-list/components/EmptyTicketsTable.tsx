import { Button, EmptyScreen, Flex } from '@edifice.io/react';
import { IconPlus } from '@edifice.io/react/icons';

import illuEmptyAdminThreads from '@images/emptyscreen/illu-actualites.svg';
import { useNavigate } from 'react-router-dom';

export default function EmptyTicketsTable() {
  const navigate = useNavigate();

  const handleCreateTicket = () => {
    navigate('/tickets/new');
  };

  return (
    <Flex direction="column" gap="24" justify="center" align="center">
      <EmptyScreen
        imageSrc={illuEmptyAdminThreads}
        imageAlt="No tickets"
        title="Aucune demande d'assistance à afficher"
        text="Veuillez revoir vos filtres, ou créez ici une nouvelle demande d'assistance."
      />
      <Button
        color="primary"
        variant="filled"
        size="md"
        onClick={handleCreateTicket}
      >
        <IconPlus /> Créer une nouvelle demande
      </Button>
    </Flex>
  );
}
