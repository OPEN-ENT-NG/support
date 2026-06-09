import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { type ReactNode } from 'react';
import { useForm } from 'react-hook-form';
import { describe, expect, it, vi } from 'vitest';
import { TicketCreateForm } from '~/features/ticket-create/components/TicketCreateForm';

vi.mock('~/hooks/usei18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
}));

vi.mock('@edifice.io/react/editor', () => ({
  Editor: () => <div data-testid="editor" />,
}));
vi.mock('~/features/ticket-create/components/TicketAttachment', () => ({
  TicketAddAttachment: () => <div data-testid="attachments" />,
}));

const CATEGORIES = [
  { label: 'Bug', value: 'bug' },
  { label: 'Question', value: 'question' },
];

const SCHOOL_A = { label: 'School A', value: 'school-a' };
const SCHOOL_B = { label: 'School B', value: 'school-b' };

const SCHOOL_PLACEHOLDER = 'support.ticket.form.school.placeholder';

function Harness({
  schoolOptions,
  onSubmit,
}: {
  schoolOptions: typeof CATEGORIES;
  onSubmit?: (data: TicketCreateForm) => void;
}) {
  const {
    register,
    setValue,
    watch,
    handleSubmit,
    formState: { errors },
  } = useForm<TicketCreateForm>({
    defaultValues: {
      category: 'bug',
      school_id: '',
      subject: 'Subject',
      description: '<p>Body</p>',
    },
  });

  return (
    <form onSubmit={handleSubmit((data) => onSubmit?.(data))}>
      <div data-testid="school_id-value">{watch('school_id')}</div>
      <TicketCreateForm
        register={register}
        setValue={setValue}
        errors={errors}
        categories={CATEGORIES}
        schoolOptions={schoolOptions}
        editorRef={undefined}
        onAttachmentsChange={() => {}}
      />
      <button type="submit">submit</button>
    </form>
  );
}

function makeWrapper() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  );
}

function renderForm(schoolOptions: typeof CATEGORIES) {
  return render(<Harness schoolOptions={schoolOptions} />, {
    wrapper: makeWrapper(),
  });
}

const schoolValue = () =>
  screen.getByTestId('school_id-value').textContent ?? '';

describe('TicketCreateForm — school selection', () => {
  it('auto-selects the only school and hides the school dropdown', async () => {
    renderForm([SCHOOL_A]);

    expect(
      screen.queryByRole('button', { name: new RegExp(SCHOOL_PLACEHOLDER) }),
    ).not.toBeInTheDocument();
    await waitFor(() => expect(schoolValue()).toBe(SCHOOL_A.value));
  });

  it('renders the school dropdown with no preselection when several schools exist', () => {
    renderForm([SCHOOL_A, SCHOOL_B]);

    expect(
      screen.getByRole('button', { name: new RegExp(SCHOOL_PLACEHOLDER) }),
    ).toBeInTheDocument();
  });

  it('writes the chosen school to the form when the user picks one', async () => {
    renderForm([SCHOOL_A, SCHOOL_B]);
    const user = userEvent.setup();

    await user.click(
      screen.getByRole('button', { name: new RegExp(SCHOOL_PLACEHOLDER) }),
    );
    await user.click(screen.getByRole('menuitem', { name: SCHOOL_B.label }));

    await waitFor(() => expect(schoolValue()).toBe(SCHOOL_B.value));
  });

  it('drops the previous auto selection when schoolOptions grows from 1 to 2', async () => {
    const { rerender } = renderForm([SCHOOL_A]);
    await waitFor(() => expect(schoolValue()).toBe(SCHOOL_A.value));

    rerender(<Harness schoolOptions={[SCHOOL_A, SCHOOL_B]} />);

    expect(
      screen.getByRole('button', { name: new RegExp(SCHOOL_PLACEHOLDER) }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: SCHOOL_A.label }),
    ).not.toBeInTheDocument();
  });

  it('blocks submit after schoolOptions grows until the user picks a school', async () => {
    const onSubmit = vi.fn();
    const { rerender } = render(
      <Harness schoolOptions={[SCHOOL_A]} onSubmit={onSubmit} />,
      { wrapper: makeWrapper() },
    );
    const user = userEvent.setup();

    await waitFor(() => expect(schoolValue()).toBe(SCHOOL_A.value));

    rerender(
      <Harness schoolOptions={[SCHOOL_A, SCHOOL_B]} onSubmit={onSubmit} />,
    );
    await waitFor(() => expect(schoolValue()).toBe(''));

    await user.click(screen.getByRole('button', { name: 'submit' }));
    expect(onSubmit).not.toHaveBeenCalled();

    await user.click(
      screen.getByRole('button', { name: new RegExp(SCHOOL_PLACEHOLDER) }),
    );
    await user.click(screen.getByRole('menuitem', { name: SCHOOL_B.label }));
    await user.click(screen.getByRole('button', { name: 'submit' }));

    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    expect(onSubmit.mock.calls[0][0].school_id).toBe(SCHOOL_B.value);
  });
});
