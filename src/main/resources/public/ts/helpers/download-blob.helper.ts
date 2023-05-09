import {AxiosResponse} from "axios";

export interface IDownloadBlobHelper {
    downloadBlob(doc: AxiosResponse): void;
}

export const downloadBlobHelper: IDownloadBlobHelper = {
    downloadBlob(doc: AxiosResponse): void {
        let blob = new Blob(["\ufeff" + doc.data], {type: 'text/csv; charset=utf-8'});

        // Download the blob
        let link = document.createElement('a');
        link.href = window.URL.createObjectURL(blob);
        link.download =  doc.headers['content-disposition'].split('filename=')[1];
        link.click();
    }
}