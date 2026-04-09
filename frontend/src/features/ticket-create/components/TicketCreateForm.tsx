import { Dropdown, Flex, FormControl, Input, Label } from '@edifice.io/react';
import { Editor, EditorRef } from '@edifice.io/react/editor';
import { RefObject, useEffect, useState } from 'react';
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
  const [attachments, setAttachments] = useState<TicketAttachment[]>([]);
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
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

  const sortedSchools = [...schoolOptions].sort((a, b) =>
    a.label.localeCompare(b.label),
  );

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
          <Dropdown block>
            <Dropdown.Trigger
              size="lg"
              block
              label={
                categories.find((c) => c.value === selectedCategory)?.label ??
                'Sélectionnez la catégorie...'
              }
            />
            <Dropdown.Menu>
              <Dropdown.SearchInput
                placeholder="Rechercher une catégorie..."
                noResultsLabel="Pas de résultat"
              />
              {categories.map((cat) => (
                <Dropdown.Item
                  key={`${cat.label}-${cat.value}`}
                  searchValue={cat.label}
                  onClick={() => {
                    setSelectedCategory(cat.value);
                    setValue('category', cat.value, {
                      shouldValidate: true,
                      shouldDirty: true,
                    });
                  }}
                >
                  {cat.label}
                </Dropdown.Item>
              ))}
            </Dropdown.Menu>
          </Dropdown>
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
            <Dropdown block>
              <Dropdown.Trigger
                size="lg"
                block
                label={
                  sortedSchools.find((s) => s.value === selectedSchool)
                    ?.label ?? "Sélectionnez l'établissement..."
                }
              />
              <Dropdown.Menu>
                <Dropdown.SearchInput
                  placeholder="Rechercher un établissement..."
                  noResultsLabel="Pas de résultat"
                />
                {sortedSchools.map((school) => (
                  <Dropdown.Item
                    key={school.value}
                    searchValue={school.label}
                    onClick={() => {
                      setSelectedSchool(school.value);
                      setValue('school_id', school.value, {
                        shouldValidate: true,
                        shouldDirty: true,
                      });
                    }}
                  >
                    {school.label}
                  </Dropdown.Item>
                ))}
              </Dropdown.Menu>
            </Dropdown>
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
