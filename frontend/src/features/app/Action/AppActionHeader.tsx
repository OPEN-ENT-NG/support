import {
  Button,
  Flex,
  IconButton,
  Loading,
  Modal,
  useIsAdmc,
  useIsAdml,
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
import { useI18n } from '~/hooks/usei18n';
import { useCanEscalate } from '~/hooks/useCanEscalate';
import { useIsTicketEscalated } from '~/hooks/useIsTicketEscalated';
import { useIsTicketNewOrOpen } from '~/hooks/useIsTicketNewOrOpen';
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

type InfoModalType = 'escalate' | 'sync' | null;

export const AppActionHeader = () => {
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [infoModal, setInfoModal] = useState<InfoModalType>(null);
  const { t } = useI18n();

  const navigate = useNavigate();
  const toast = useToast();
  const queryClient = useQueryClient();
  const location = useLocation();
  const canEscalate = useCanEscalate();
  const isTicketEscalated = useIsTicketEscalated();
  const isTicketNewOrOpen = useIsTicketNewOrOpen();
  const { isAdmc } = useIsAdmc();
  const { isAdml } = useIsAdml();

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
        toast.info(t('support.header.export.info'));
        await workerExportTickets('*');
      } else {
        directExportTickets('*');
      }
    } catch (err) {
      console.error(err);
      toast.error(t('support.header.export.error'));
    }
  }, [toast]);

  const escalateTicket = useCallback(async () => {
    setIsLoading(true);

    try {
      await escalateTickets([ticketId!]);
      toast.success(t('support.header.escalate.success'));
      await queryClient.invalidateQueries({
        queryKey: [ticketsQueryKeys.all()],
      });
    } catch (e) {
      console.error(e);
      toast.error(t('support.header.escalate.error'));
    } finally {
      setIsLoading(false);
    }
  }, [ticketId, toast, queryClient]);

  const updateTicket = useCallback(async () => {
    setIsLoading(true);

    try {
      await refreshTicket(ticketId!);
      toast.success(t('support.header.update.success'));
      await queryClient.invalidateQueries({
        queryKey: [ticketsQueryKeys.all()],
      });
    } catch (e) {
      console.error(e);
      toast.error(t('support.header.update.error'));
    } finally {
      setIsLoading(false);
    }
  }, [ticketId, toast, queryClient]);

  const closeInfoModal = useCallback(() => {
    setInfoModal(null);
  }, []);

  const pageActions = useMemo<PageAction[]>(
    () => [
      {
        id: 'export',
        visibility: isListRoute && (isAdml || isAdmc),
        element: (
          <Button
            leftIcon={<IconDownload />}
            color="primary"
            variant="outline"
            onClick={exportAllTickets}
          >
            {t('support.ticket.export.all')}
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
            {t('support.ticket.create')}
          </Button>
        ),
      },
      {
        id: 'escalate',
        visibility:
          isTicketRoute &&
          canEscalate &&
          !isTicketEscalated &&
          isTicketNewOrOpen,
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
              {isLoading ? <Loading isLoading /> : t('support.header.escalate.button')}
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
              {isLoading ? <Loading isLoading /> : t('support.header.update.button')}
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
            <em>{t('support.header.escalated.message')}</em>
          </p>
        ),
      },
    ],
    [
      isListRoute,
      isTicketRoute,
      canEscalate,
      isTicketEscalated,
      isTicketNewOrOpen,
      isAdmc,
      isAdml,
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
        onModalClose={closeInfoModal}
      >
        <ModalHeader onModalClose={closeInfoModal}>
          {t('support.header.escalate.modal.title')}
        </ModalHeader>
        <ModalBody>
          <p>{t('support.header.escalate.modal.body')}</p>
        </ModalBody>
        <ModalFooter>
          <Flex justify="end">
            <Button onClick={closeInfoModal}>{t('support.header.escalate.modal.close')}</Button>
          </Flex>
        </ModalFooter>
      </Modal>

      <Modal
        id="info-modal-sync"
        isOpen={infoModal === 'sync'}
        onModalClose={closeInfoModal}
      >
        <ModalHeader onModalClose={closeInfoModal}>
          {t('support.header.sync.modal.title')}
        </ModalHeader>
        <ModalBody>
          <p>{t('support.header.sync.modal.body')}</p>
        </ModalBody>
        <ModalFooter>
          <Flex justify="end">
            <Button onClick={closeInfoModal}>{t('support.header.sync.modal.close')}</Button>
          </Flex>
        </ModalFooter>
      </Modal>
    </>
  );
};
