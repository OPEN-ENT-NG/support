import { Flex, FormControl, Input, Label, Select } from '@edifice.io/react';
import { Editor, EditorRef } from '@edifice.io/react/editor';
import { RefObject } from 'react';
import { FieldErrors, UseFormRegister, UseFormSetValue } from 'react-hook-form';
import { TicketAttachment } from '~/models';
import TicketAddAttachment from './TicketAttachment';

export type TicketCreateForm = {
  category: string;
  school_id: string;
  subject: string;
  description: string;
};

type SelectOption = { label: string; value: string };

type TicketCreateFormProps = {
  register: UseFormRegister<TicketCreateForm>;
  setValue: UseFormSetValue<TicketCreateForm>;
  errors: FieldErrors<TicketCreateForm>;
  categories: SelectOption[];
  schoolOptions: SelectOption[];
  editorRef: RefObject<EditorRef> | undefined;
  onAttachmentsChange: (attachments: TicketAttachment[]) => void;
};

export default function TicketCreateForm({
  register,
  setValue,
  errors,
  categories,
  schoolOptions,
  editorRef,
  onAttachmentsChange,
}: TicketCreateFormProps) {
  return (
    <>
      <Flex direction="row" wrap="wrap" gap="8">
        <FormControl
          id="category"
          isRequired
          status={errors.category ? 'invalid' : undefined}
        >
          <Label>Catégorie</Label>
          <input
            type="hidden"
            {...register('category', {
              required: 'La catégorie est obligatoire',
            })}
          />
          <Select
            size="lg"
            options={categories}
            placeholderOption="Sélectionnez la catégorie..."
            onValueChange={(value) =>
              setValue('category', value as string, {
                shouldValidate: true,
                shouldDirty: true,
              })
            }
          />
        </FormControl>
        {schoolOptions.length > 1 && (
          <FormControl
            id="school_id"
            isRequired
            status={errors.school_id ? 'invalid' : undefined}
          >
            <Label>Établissement</Label>
            <input
              type="hidden"
              {...register('school_id', {
                required: "L'établissement est obligatoire",
              })}
            />
            <Select
              size="lg"
              options={schoolOptions}
              placeholderOption="Sélectionnez l'établissement..."
              onValueChange={(value) =>
                setValue('school_id', value as string, {
                  shouldValidate: true,
                  shouldDirty: true,
                })
              }
            />
          </FormControl>
        )}
      </Flex>

      <FormControl
        id="subject"
        isRequired
        status={errors.subject ? 'invalid' : undefined}
      >
        <Label>Sujet</Label>
        <Input
          type="text"
          size="lg"
          placeholder="Décrivez le sujet..."
          maxLength={255}
          {...register('subject', { required: 'Le sujet est obligatoire' })}
        />
      </FormControl>

      <FormControl
        id="description"
        isRequired
        status={errors.description ? 'invalid' : undefined}
      >
        <Label>Détails de votre demande</Label>
        <input
          type="hidden"
          {...register('description', {
            validate: (v) =>
              v.replace(/<[^>]*>/g, '').trim() !== '' ||
              'La description est obligatoire',
          })}
        />
        <div className="editor-container">
          <Editor
            ref={editorRef}
            id="description"
            content=""
            placeholder="Saisissez votre texte ici"
            mode={'edit'}
            onContentChange={({ editor }) => {
              setValue('description', editor.getHTML(), {
                shouldValidate: true,
                shouldDirty: true,
              });
            }}
          />
        </div>
      </FormControl>

      <TicketAddAttachment onChange={onAttachmentsChange} />
    </>
  );
}
