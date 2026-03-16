import { Button, Flex } from '@edifice.io/react';
import { IconArrowLeft } from '@edifice.io/react/icons';
import { useNavigate } from 'react-router-dom';
import { useTicketCreateForm } from '../hooks/useTicketCreateForm';
import { useTicketFormOptions } from '~/hooks';
import TicketCreateActions from './TicketCreateActions';
import TicketCreateForm from './TicketCreateForm';

export function TicketCreate() {
  const navigate = useNavigate();
  const { categories, schoolOptions } = useTicketFormOptions();
  const {
    register,
    setValue,
    errors,
    isValid,
    isPending,
    editorRef,
    attachmentsRef,
    onSubmit,
    onSubmitAndEscalate,
  } = useTicketCreateForm();

  return (
    <>
      <Flex
        className="m-auto w-100"
        direction="column"
        gap="16"
        style={{ maxWidth: '900px' }}
      >
        <Flex direction="row">
          <Button
            color="tertiary"
            variant="ghost"
            leftIcon={<IconArrowLeft />}
            onClick={() => navigate('/')}
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
        />
      </Flex>
    </>
  );
}
