import { Button, Modal, WorkspaceFolders } from '@edifice.io/react';
import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';

interface AddAttachmentToWorkspaceModalProps {
  isOpen?: boolean;
  onModalClose: () => void;
  onCopyToWorkspace: (folderId: string) => Promise<boolean>;
}

export function AddAttachmentToWorkspaceModal({
  isOpen = false,
  onModalClose,
  onCopyToWorkspace,
}: AddAttachmentToWorkspaceModalProps) {
  const [selectedFolderId, setSelectedFolderId] = useState<string | undefined>(
    undefined,
  );
  const [isLoading, setIsLoading] = useState(false);
  const [disabled, setDisabled] = useState(true);

  const handleFolderSelected = (folderId: string, canCopyFileInto: boolean) => {
    setSelectedFolderId(canCopyFileInto ? folderId : undefined);
  };

  const handleConfirm = async () => {
    if (!selectedFolderId) return;
    setIsLoading(true);
    const isSuccess = await onCopyToWorkspace(selectedFolderId);
    if (isSuccess) onModalClose();
    setIsLoading(false);
  };

  useEffect(() => {
    setDisabled(selectedFolderId === undefined);
  }, [selectedFolderId]);

  return createPortal(
    <Modal
      isOpen={isOpen}
      onModalClose={onModalClose}
      id="add-attachment-to-workspace-modal"
      size="md"
    >
      <Modal.Header onModalClose={onModalClose}>
        Copier dans l&apos;espace documentaire
      </Modal.Header>
      <Modal.Body>
        <div className="d-flex flex-column gap-12">
          <p>Sélectionnez le dossier de destination.</p>
          <WorkspaceFolders onFolderSelected={handleFolderSelected} />
        </div>
      </Modal.Body>
      <Modal.Footer>
        <Button
          type="button"
          color="tertiary"
          variant="ghost"
          onClick={onModalClose}
        >
          Annuler
        </Button>
        <Button
          type="button"
          color="primary"
          variant="filled"
          onClick={handleConfirm}
          disabled={isLoading || disabled}
          isLoading={isLoading}
        >
          Copier
        </Button>
      </Modal.Footer>
    </Modal>,
    document.getElementById('portal') as HTMLElement,
  );
}
