import {
  Button,
  Flex,
  IconButton,
  Loading,
  Modal,
  useIsAdmc,
  useToast,
} from '@edifice.io/react';
import { useQueryClient } from '@tanstack/react-query';
import {
  IconDownload,
  IconInfoCircle,
  IconPlus,
  IconRefresh,
  IconSend,
} from '@edifice.io/react/icons';
import ModalBody from 'node_modules/@edifice.io/react/dist/components/Modal/ModalBody';
import ModalFooter from 'node_modules/@edifice.io/react/dist/components/Modal/ModalFooter';
import ModalHeader from 'node_modules/@edifice.io/react/dist/components/Modal/ModalHeader';
import { ReactNode, useCallback, useMemo, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { useCanEscalate } from '~/hooks/useCanEscalate';
import { useIsTicketEscalated } from '~/hooks/useIsTicketEscalated';
import {
  countTickets,
  directExportTickets,
  escalateTickets,
  getThresholdDirectExportTickets,
  refreshTicket,
  workerExportTickets,
} from '~/services/api';
import { ticketsQueryKeys } from '~/services/queries/tickets';

export type PageAction = {
  id: string;
  visibility: boolean;
  element: ReactNode;
};

export const AppActionHeader = () => {
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [infoModal, setInfoModal] = useState<'escalate' | 'sync' | null>(null);

  const navigate = useNavigate();
  const toast = useToast();
  const queryClient = useQueryClient();
  const location = useLocation();
  const canEscalate = useCanEscalate();
  const isTicketEscalated = useIsTicketEscalated();
  const { isAdmc } = useIsAdmc();

  const { ticketId } = useParams();

  const isListRoute = /^\/$/.test(location.pathname); // Check if path is "/"
  const isTicketRoute = /^\/tickets\/\d+$/.test(location.pathname); // Check if path is "/ticket/{ticket id}"

  const exportAllTickets = useCallback(async () => {
    try {
      const [threshold, count] = await Promise.all([
        getThresholdDirectExportTickets(),
        countTickets('*'),
      ]);
      if (count > threshold) {
        toast.info(
          "L'export est en cours, vous recevrez le fichier dans votre espace de travail.",
        );
        await workerExportTickets('*');
      } else {
        directExportTickets('*');
      }
    } catch (err) {
      console.error(err);
      toast.error("Erreur lors de l'export des tickets.");
    }
  }, [toast]);

  const escalateTicket = useCallback(async () => {
    setIsLoading(true);

    try {
      await escalateTickets([ticketId!]);
      toast.success('Ticket escaladé avec succès.');
      await queryClient.invalidateQueries({
        queryKey: [ticketsQueryKeys.all()],
      });
    } catch (e) {
      console.error(e);
      toast.error("Erreur lors de l'escalade du ticket.");
    } finally {
      setIsLoading(false);
    }
  }, [ticketId, toast, queryClient]);

  const updateTicket = useCallback(async () => {
    setIsLoading(true);

    try {
      await refreshTicket(ticketId!);
      toast.success('Le ticket a été mis à jour.');
    } catch (e) {
      console.error(e);
      toast.error("Le ticket n'a pas pu être mis à jour.");
    } finally {
      setIsLoading(false);
    }
  }, [ticketId, toast]);

  const pageActions = useMemo<PageAction[]>(
    () => [
      {
        id: 'export',
        visibility: isListRoute,
        element: (
          <Button
            leftIcon={<IconDownload />}
            color="primary"
            variant="outline"
            onClick={exportAllTickets}
          >
            Exporter toutes les demandes
          </Button>
        ),
      },
      {
        id: 'new',
        visibility: isListRoute,
        element: (
          <Button
            leftIcon={<IconPlus />}
            color="primary"
            onClick={() => navigate('/tickets/new')}
          >
            Nouvelle demande
          </Button>
        ),
      },
      {
        id: 'escalate',
        visibility: isTicketRoute && canEscalate && !isTicketEscalated,
        element: (
          <Flex gap="12">
            <IconButton
              aria-label="Open info modal"
              color="tertiary"
              variant="ghost"
              icon={<IconInfoCircle />}
              onClick={() => setInfoModal('escalate')}
            />
            <Button
              leftIcon={!isLoading && <IconSend />}
              color="primary"
              onClick={() => escalateTicket()}
            >
              {isLoading ? <Loading isLoading /> : 'Transmettre au support ENT'}
            </Button>
          </Flex>
        ),
      },
      {
        id: 'sync',
        visibility: isTicketRoute && isAdmc && canEscalate && isTicketEscalated,
        element: (
          <Flex gap="12">
            <IconButton
              aria-label="Open info modal"
              color="tertiary"
              variant="ghost"
              icon={<IconInfoCircle />}
              onClick={() => setInfoModal('sync')}
            />
            <Button
              leftIcon={!isLoading && <IconRefresh />}
              color="primary"
              onClick={() => updateTicket()}
            >
              {isLoading ? <Loading isLoading /> : 'Mettre à jour le ticket'}
            </Button>
          </Flex>
        ),
      },
      {
        id: 'escalated-info',
        visibility:
          !isAdmc && isTicketRoute && canEscalate && isTicketEscalated,
        element: (
          <p className="text-escalated">
            <em>Le ticket a été transmis au support ENT</em>
          </p>
        ),
      },
    ],
    [
      isListRoute,
      isTicketRoute,
      canEscalate,
      isTicketEscalated,
      isAdmc,
      isLoading,
      navigate,
      exportAllTickets,
      escalateTicket,
      updateTicket,
    ],
  );

  return (
    <>
      <Flex fill align="center" justify="end" gap="12">
        {pageActions
          .filter((action) => action.visibility)
          .map((action) => (
            <div key={action.id}>{action.element}</div>
          ))}
      </Flex>

      <Modal
        id="info-modal-escalate"
        isOpen={infoModal === 'escalate'}
        onModalClose={() => setInfoModal(null)}
      >
        <ModalHeader onModalClose={() => setInfoModal(null)}>
          Transmettre au support ENT
        </ModalHeader>
        <ModalBody>
          <p>Dessin, blabla pour expliquer le principe de l'escalade !</p>
        </ModalBody>
        <ModalFooter>
          <Flex justify="end">
            <Button onClick={() => setInfoModal(null)}>Fermer</Button>
          </Flex>
        </ModalFooter>
      </Modal>

      <Modal
        id="info-modal-sync"
        isOpen={infoModal === 'sync'}
        onModalClose={() => setInfoModal(null)}
      >
        <ModalHeader onModalClose={() => setInfoModal(null)}>
          Synchronisation avec le ticket escaladé
        </ModalHeader>
        <ModalBody>
          <p>Dessin, blabla pour expliquer le principe des 15 minutes</p>
        </ModalBody>
        <ModalFooter>
          <Flex justify="end">
            <Button onClick={() => setInfoModal(null)}>Fermer</Button>
          </Flex>
        </ModalFooter>
      </Modal>
    </>
  );
};
