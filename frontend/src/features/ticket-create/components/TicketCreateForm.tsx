import { Flex, FormControl, Input, Label } from '@edifice.io/react';
import { Editor, EditorRef } from '@edifice.io/react/editor';
import { RefObject, useEffect, useState } from 'react';
import { FieldErrors, UseFormRegister, UseFormSetValue } from 'react-hook-form';
import { TicketAttachment } from '~/models';
import { useI18n } from '~/hooks/usei18n';
import { SearchableDropdown } from '~/components/SearchableDropdown';
import { TicketAddAttachment } from './TicketAttachment';

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

export function TicketCreateForm({
  register,
  setValue,
  errors,
  categories,
  schoolOptions,
  editorRef,
  onAttachmentsChange,
}: TicketCreateFormProps) {
  const { t } = useI18n();
  const [attachments, setAttachments] = useState<TicketAttachment[]>([]);
  const [selectedCategory, setSelectedCategory] = useState('');
  const [selectedSchool, setSelectedSchool] = useState('');

  useEffect(() => {
    if (schoolOptions.length === 1) {
      setSelectedSchool(schoolOptions[0].value);
    }
  }, [schoolOptions]);

  const handleAttachmentsChange = (
    updater: (prev: TicketAttachment[]) => TicketAttachment[],
  ) => {
    setAttachments((prev) => {
      const updated = updater(prev);
      onAttachmentsChange(updated);
      return updated;
    });
  };

  return (
    <>
      <Flex direction="row" wrap="wrap" gap="8">
        <FormControl
          id="category"
          isRequired
          status={errors.category ? 'invalid' : undefined}
        >
          <input
            type="hidden"
            {...register('category', {
              required: t('support.ticket.form.category.required'),
            })}
          />
          <SearchableDropdown
            id="category-dropdown"
            label={t('support.ticket.category')}
            placeholder={t('support.ticket.form.category.placeholder')}
            searchPlaceholder={t('support.ticket.form.search.category')}
            options={categories}
            selectedValue={selectedCategory}
            onChange={(value) => {
              setSelectedCategory(value);
              setValue('category', value, {
                shouldValidate: true,
                shouldDirty: true,
              });
            }}
            isRequired
            isInvalid={!!errors.category}
            size="lg"
          />
        </FormControl>

        {schoolOptions.length > 1 && (
          <FormControl
            id="school_id"
            isRequired
            status={errors.school_id ? 'invalid' : undefined}
          >
            <input
              type="hidden"
              {...register('school_id', {
                required: t('support.ticket.form.school.required'),
              })}
            />
            <SearchableDropdown
              id="school-dropdown"
              label={t('support.ticket.school')}
              placeholder={t('support.ticket.form.school.placeholder')}
              searchPlaceholder={t('support.ticket.form.search.school')}
              options={schoolOptions}
              selectedValue={selectedSchool}
              onChange={(value) => {
                setSelectedSchool(value);
                setValue('school_id', value, {
                  shouldValidate: true,
                  shouldDirty: true,
                });
              }}
              isRequired
              isInvalid={!!errors.school_id}
              size="lg"
            />
          </FormControl>
        )}
      </Flex>

      <FormControl
        id="subject"
        isRequired
        status={errors.subject ? 'invalid' : undefined}
      >
        <Label>{t('support.ticket.subject')}</Label>
        <Input
          type="text"
          size="lg"
          placeholder={t('support.ticket.form.subject.placeholder')}
          maxLength={255}
          {...register('subject', {
            required: t('support.ticket.form.subject.required'),
          })}
        />
      </FormControl>

      <FormControl
        id="description"
        isRequired
        status={errors.description ? 'invalid' : undefined}
      >
        <Label>{t('support.ticket.form.description.label')}</Label>
        <input
          type="hidden"
          {...register('description', {
            validate: (v) =>
              v.replace(/<[^>]*>/g, '').trim() !== '' ||
              t('support.ticket.form.description.required'),
          })}
        />
        <div className="editor-container">
          <Editor
            ref={editorRef}
            id="description"
            content=""
            placeholder={t('support.ticket.form.description.placeholder')}
            mode={'edit'}
            focus={false}
            onContentChange={({ editor }) => {
              setValue('description', editor.getHTML(), {
                shouldValidate: true,
                shouldDirty: true,
              });
            }}
          />
        </div>
      </FormControl>

      <TicketAddAttachment
        attachments={attachments}
        onChange={handleAttachmentsChange}
      />
    </>
  );
}
