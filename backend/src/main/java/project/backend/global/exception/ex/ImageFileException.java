package project.backend.global.exception.ex;

import project.backend.global.exception.errorcode.ImageFileErrorCode;


public class ImageFileException extends BaseException {

    public ImageFileException(ImageFileErrorCode errorCode) {
        super(errorCode);
    }
}