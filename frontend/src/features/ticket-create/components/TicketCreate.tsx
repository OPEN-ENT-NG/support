import { Button, Flex } from '@edifice.io/react';
import { IconArrowLeft } from '@edifice.io/react/icons';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTicketFormOptions } from '~/hooks';
import { useTicketCreateForm } from '../hooks/useTicketCreateForm';
import TicketCancelModal from './TicketCancelModal';
import TicketCreateActions from './TicketCreateActions';
import TicketCreateForm from './TicketCreateForm';

export function TicketCreate() {
  const navigate = useNavigate();
  const [cancelModalVisible, setCancelModalVisible] = useState(false);

  const { categories, schoolOptions } = useTicketFormOptions();
  const {
    register,
    setValue,
    errors,
    isValid,
    isDirty,
    isPending,
    editorRef,
    attachmentsRef,
    onSubmit,
    onSubmitAndEscalate,
  } = useTicketCreateForm();

  const handleCancelClick = () => {
    if (isDirty) {
      setCancelModalVisible(true);
    } else {
      navigate('/');
    }
  };

  return (
    <>
      <Flex
        className="m-auto w-100 ticket-create-form"
        direction="column"
        gap="16"
      >
        <Flex direction="row">
          <Button
            color="tertiary"
            variant="ghost"
            leftIcon={<IconArrowLeft />}
            onClick={handleCancelClick}
          >
            Retour
          </Button>
        </Flex>

        <TicketCreateForm
          register={register}
          setValue={setValue}
          errors={errors}
          categories={categories}
          schoolOptions={schoolOptions}
          editorRef={editorRef}
          onAttachmentsChange={(uploaded) =>
            (attachmentsRef.current = uploaded)
          }
        />

        <TicketCreateActions
          isPending={isPending}
          isValid={isValid}
          onSubmit={onSubmit}
          onSubmitAndEscalate={onSubmitAndEscalate}
          onCancelClick={handleCancelClick}
        />
      </Flex>

      <TicketCancelModal
        isOpen={cancelModalVisible}
        onClose={() => setCancelModalVisible(false)}
        onConfirm={() => {
          setCancelModalVisible(false);
          navigate('/');
        }}
      />
    </>
  );
}
