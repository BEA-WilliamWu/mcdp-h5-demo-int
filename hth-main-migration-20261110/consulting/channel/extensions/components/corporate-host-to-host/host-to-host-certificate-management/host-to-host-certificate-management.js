define([
    "knockout",
    "ojL10n!extensions/resources/nls/host-to-host-certificate-management",
    "framework/js/constants/constants",
    "./model",
    "ojs/ojbutton",
    "ojs/ojfilepicker"
], function(ko, resourceBundle, Constants, CertificateModel) {
    "use strict";

    const ACCEPTED_FILE_TYPES = [".pem", ".PEM"],
        DEFAULT_DOWNLOAD_FILE_NAME = "certificate.pem",
        MAX_CERTIFICATE_COUNT = 2,
        MAX_PEM_CONTENT_BYTES = 64 * 1024,
        PEM_FILE_PATTERN = /\.pem$/i;

    function unwrapValue(value) {
        const unwrapped = ko.utils.unwrapObservable(value);

        if (unwrapped === undefined || unwrapped === null) {
            return "";
        }

        if (typeof unwrapped === "object") {
            return unwrapValue(unwrapped.displayValue || unwrapped.value || unwrapped.name || "");
        }

        return String(unwrapped);
    }

    function getTransactionDetails(rootModel) {
        return rootModel.transactionDetails && rootModel.transactionDetails()
            ? rootModel.transactionDetails()
            : {};
    }

    function getUserProfile(rootParams) {
        return rootParams.dashboard && rootParams.dashboard.userData
            ? rootParams.dashboard.userData.userProfile || {}
            : {};
    }

    function getComponentParams(params) {
        return params && params.data
            ? params.data
            : {};
    }

    function getConfirmScreenExtensions(params) {
        return params && params.confirmScreenExtensions
            ? params.confirmScreenExtensions
            : {};
    }

    function getCertificateEntries(data) {
        return (data && (data.certificates || data.certificateFiles)) || [];
    }

    function normalizeCertificateEntry(fileEntry) {
        return {
            id: unwrapValue(fileEntry && (fileEntry.certificateId || fileEntry.fileId || fileEntry.id)),
            name: unwrapValue(fileEntry && (fileEntry.fileName || fileEntry.name)),
            pemContent: unwrapValue(fileEntry && fileEntry.pemContent)
        };
    }

    function normalizePemContent(pemContent) {
        return unwrapValue(pemContent).replace(/\r\n/g, "\n").trim();
    }

    function normalizeFileName(fileEntry) {
        return unwrapValue(fileEntry && (fileEntry.name || fileEntry.fileName)).toLowerCase();
    }

    function getFileFromEntry(fileEntry) {
        return fileEntry && (fileEntry.file || fileEntry);
    }

    function hasDuplicateFileName(fileEntries) {
        const fileNames = {};

        for (let i = 0; i < fileEntries.length; i++) {
            const fileName = normalizeFileName(fileEntries[i]);

            if (fileNames[fileName]) {
                return true;
            }

            fileNames[fileName] = true;
        }

        return false;
    }

    function hasDuplicatePemContent(certificates) {
        const pemContents = {};

        for (let i = 0; i < certificates.length; i++) {
            const pemContent = normalizePemContent(certificates[i].pemContent);

            if (pemContents[pemContent]) {
                return true;
            }

            pemContents[pemContent] = true;
        }

        return false;
    }

    function hasOversizedFile(fileEntries) {
        return fileEntries.some(function(fileEntry) {
            const file = getFileFromEntry(fileEntry);

            return file && typeof file.size === "number" && file.size > MAX_PEM_CONTENT_BYTES;
        });
    }

    function toUploadEntry(file) {
        return {
            name: file.name,
            file: file
        };
    }

    function isFailureResponse(data) {
        const result = data && data.status && data.status.result
                ? String(data.status.result).toUpperCase()
                : "",
            messageType = data && data.status && data.status.message && data.status.message.type
                ? String(data.status.message.type).toUpperCase()
                : "";

        return result === "FAILED" || result === "FAILURE" || messageType === "ERROR";
    }

    function isHttpError(error) {
        return error && typeof error.status === "number";
    }

    function isHandledValidationError(error) {
        return error && error.handledValidation;
    }

    function rejectHandledValidation() {
        return Promise.reject({
            handledValidation: true
        });
    }

    function downloadLocalPem(fileEntry) {
        const blob = new Blob([fileEntry.pemContent], {
                type: "application/x-pem-file"
            }),
            urlFactory = window.URL || window.webkitURL,
            downloadUrl = urlFactory.createObjectURL(blob),
            downloadLink = document.createElement("a");

        downloadLink.href = downloadUrl;
        downloadLink.download = fileEntry.name || DEFAULT_DOWNLOAD_FILE_NAME;
        document.body.appendChild(downloadLink);
        downloadLink.click();
        document.body.removeChild(downloadLink);
        urlFactory.revokeObjectURL(downloadUrl);
    }

    function readFileAsText(fileEntry) {
        return new Promise(function(resolve, reject) {
            const reader = new FileReader();

            reader.onload = function(loadEvent) {
                resolve({
                    fileName: fileEntry.name,
                    pemContent: normalizePemContent(loadEvent.target.result)
                });
            };

            reader.onerror = function() {
                reject(reader.error);
            };

            reader.readAsText(fileEntry.file);
        });
    }

    return function(rootParams) {
        let userInteractionStarted = false,
            submittedFiles = [];

        const self = this,
            rootModel = rootParams.rootModel || {},
            params = rootModel.params || {},
            transactionDetails = getTransactionDetails(rootModel),
            userProfile = getUserProfile(rootParams),
            componentParams = getComponentParams(params),
            confirmScreenExtensions = getConfirmScreenExtensions(params),
            approvalMode = params.mode === "approval",
            showMessage = function(message, type) {
                if (rootParams.baseModel && rootParams.baseModel.showMessages) {
                    rootParams.baseModel.showMessages(null, [message], type);
                }
            },
            showResponseMessages = function(response) {
                if (rootParams.baseModel && rootParams.baseModel.showMessages) {
                    rootParams.baseModel.showMessages({
                        responseJSON: response
                    });
                }
            },
            getPartyNameFromEntities = function() {
                const entities = userProfile.accessibleEntityDTOs;

                if (entities && entities.length) {
                    for (let i = 0; i < entities.length; i++) {
                        if (entities[i].entityId === Constants.currentEntity) {
                            return unwrapValue(entities[i].partyName);
                        }
                    }

                    return unwrapValue(entities[0].partyName);
                }

                return "";
            },
            resolveTransactionReferenceNumber = function(data) {
                return unwrapValue(data && data.status && data.status.referenceNumber)
                    || unwrapValue(data && data.contextID)
                    || self.nls.labels.notAvailable;
            },
            readSelectedFiles = function() {
                return Promise.all(self.uploadedFiles().map(readFileAsText));
            },
            getNamedCertificates = function(data) {
                return getCertificateEntries(data).map(normalizeCertificateEntry).filter(function(fileEntry) {
                    return fileEntry.name;
                });
            },
            canApplyInitialSearchResponse = function() {
                return !userInteractionStarted && !self.isSubmitting();
            },
            populateCertificates = function(data) {
                const certificates = getNamedCertificates(data);

                self.currentCertificateFiles(certificates);
                self.currentCertificateFileName(certificates.length ? certificates[0].name : "");
                self.confirmationFiles(certificates);
                self.mode(certificates.length ? "read" : "edit");
            },
            populateFromApprovalSnapshot = function(data) {
                const snapshot = data || {},
                    certificates = getNamedCertificates(snapshot),
                    partyId = unwrapValue(snapshot.partyId || transactionDetails.partyId),
                    accountNumber = unwrapValue(snapshot.accountNumber || snapshot.partyId || transactionDetails.partyId),
                    companyName = unwrapValue(snapshot.companyName || snapshot.accountName
                        || (snapshot.partyName && snapshot.partyName.fullName)
                        || (transactionDetails.partyName && transactionDetails.partyName.fullName)),
                    referenceNumber = unwrapValue(snapshot.referenceNumber || params.transactionId);

                self.partyId(partyId || self.partyId());
                self.accountNumber(accountNumber || self.accountNumber());
                self.companyName(companyName || self.companyName());
                self.currentCertificateFiles(certificates);
                self.currentCertificateFileName(certificates.length ? certificates[0].name : "");
                self.confirmationFiles(certificates);
                self.transactionReferenceNumber(referenceNumber || self.transactionReferenceNumber());
                self.mode("read");
            },
            validateSelectedFileMetadata = function(selectedFiles, nextFiles) {
                const hasInvalidFile = selectedFiles.some(function(file) {
                        return !file || !file.name || !PEM_FILE_PATTERN.test(file.name);
                    }),
                    hasEmptyFile = selectedFiles.some(function(file) {
                        return file && typeof file.size === "number" && file.size <= 0;
                    });

                if (hasInvalidFile) {
                    showMessage(self.nls.messages.invalidPemFile, "ERROR");

                    return false;
                }

                if (hasEmptyFile) {
                    showMessage(self.nls.messages.emptyPemFile, "ERROR");

                    return false;
                }

                if (hasOversizedFile(selectedFiles)) {
                    showMessage(self.nls.messages.pemFileTooLarge, "ERROR");

                    return false;
                }

                if (nextFiles.length > self.maximumFiles) {
                    showMessage(self.nls.messages.maximumPemFiles, "ERROR");

                    return false;
                }

                if (hasDuplicateFileName(nextFiles)) {
                    showMessage(self.nls.messages.duplicateFileName, "ERROR");

                    return false;
                }

                return true;
            },
            validateReadCertificates = function(certificates) {
                if (certificates.some(function(certificate) {
                    return !normalizePemContent(certificate.pemContent);
                })) {
                    showMessage(self.nls.messages.emptyPemFile, "ERROR");

                    return rejectHandledValidation();
                }

                if (hasDuplicatePemContent(certificates)) {
                    showMessage(self.nls.messages.duplicatePemContent, "ERROR");

                    return rejectHandledValidation();
                }

                return certificates;
            },
            submitReadCertificates = function(certificates) {
                submittedFiles = certificates.map(normalizeCertificateEntry);

                return CertificateModel.submit({
                    certificates: certificates
                });
            },
            handleSubmitResponse = function(data) {
                if (isFailureResponse(data)) {
                    showResponseMessages(data);

                    return;
                }

                self.transactionReferenceNumber(resolveTransactionReferenceNumber(data));
                self.confirmationFileName(submittedFiles.length ? submittedFiles[0].name : "");
                self.confirmationFiles(submittedFiles);
                self.currentCertificateFileName(self.confirmationFileName());
                self.mode("success");
            },
            handleSubmitError = function(error) {
                if (!isHttpError(error) && !isHandledValidationError(error)) {
                    showMessage(resourceBundle.messages.submitFailed, "ERROR");
                }
            },
            configureApprovalConfirmScreen = function(data) {
                const certificates = getNamedCertificates(data),
                    accountNumber = self.accountNumberDisplay();

                if (!approvalMode || !confirmScreenExtensions) {
                    return;
                }

                ko.utils.extend(confirmScreenExtensions, {
                    isSet: true,
                    taskCode: "PP_N_HTH_CERT_UPL",
                    template: "confirm-screen/host-to-host-certificate-management",
                    mode: "readonly",
                    resourceBundle: self.nls,
                    record: {
                        accountNumber: accountNumber,
                        companyName: self.companyNameDisplay(),
                        certificates: certificates
                    },
                    downloadCertificate: self.onDownloadCertificate
                });
            },
            loadExistingCertificates = function() {
                CertificateModel.search().then(function(data) {
                    if (!canApplyInitialSearchResponse()) {
                        return;
                    }

                    if (isFailureResponse(data)) {
                        self.mode("edit");

                        return;
                    }

                    self.accountNumber(unwrapValue(data.accountNumber) || self.accountNumber());
                    self.companyName(unwrapValue(data.accountName || data.companyName) || self.companyName());
                    populateCertificates(data);
                }).catch(function() {
                    if (canApplyInitialSearchResponse()) {
                        self.mode("edit");
                    }
                });
            };

        self.nls = resourceBundle;
        self.approvalMode = ko.observable(approvalMode);
        self.acceptedFileTypes = ACCEPTED_FILE_TYPES;
        self.maximumFiles = MAX_CERTIFICATE_COUNT;
        self.maximumFileSize = MAX_PEM_CONTENT_BYTES;
        self.partyId = ko.observable(unwrapValue(userProfile.partyId || componentParams.partyId));
        self.accountNumber = ko.observable(unwrapValue(componentParams.accountNumber || userProfile.partyId));

        self.companyName = ko.observable(
            unwrapValue(componentParams.companyName || userProfile.companyName)
                || getPartyNameFromEntities()
        );

        self.currentCertificateFileName = ko.observable("");
        self.currentCertificateFiles = ko.observableArray([]);
        self.mode = ko.observable("edit");
        self.selectedFile = ko.observable(null);
        self.uploadedFiles = ko.observableArray([]);
        self.confirmationFileName = ko.observable("");
        self.confirmationFiles = ko.observableArray([]);
        self.transactionReferenceNumber = ko.observable("");
        self.filePickerVisible = ko.observable(true);
        self.fileError = ko.observable("");
        self.isSubmitting = ko.observable(false);
        self.helpTitle = self.nls.help.title;
        self.helpDescription = self.nls.help.description;
        self.userGuideLabel = self.nls.labels.userGuide;

        rootParams.dashboard.headerName(self.nls.componentHeader);

        self.accountNumberDisplay = ko.pureComputed(function() {
            return self.accountNumber() || self.partyId() || self.nls.labels.notAvailable;
        });

        self.companyNameDisplay = ko.pureComputed(function() {
            return self.companyName() || self.nls.labels.notAvailable;
        });

        self.chooseFilesLabel = ko.pureComputed(function() {
            return self.nls.labels.chooseFiles;
        });

        self.isReadMode = ko.pureComputed(function() {
            return self.mode() === "read";
        });

        self.isEditMode = ko.pureComputed(function() {
            return self.mode() === "edit";
        });

        self.isSuccessMode = ko.pureComputed(function() {
            return self.mode() === "success";
        });

        self.showReadActions = ko.pureComputed(function() {
            return self.isReadMode() && !self.approvalMode();
        });

        self.isSubmitDisabled = ko.pureComputed(function() {
            return self.isSubmitting() || !self.uploadedFiles().length || !self.selectedFile() || !!self.fileError();
        });

        self.isUploadLimitReached = ko.pureComputed(function() {
            return self.uploadedFiles().length >= self.maximumFiles;
        });

        self.resetFilePickerSelection = function() {
            if (!self.filePickerVisible()) {
                return;
            }

            self.filePickerVisible(false);

            window.setTimeout(function() {
                self.filePickerVisible(true);
            }, 0);
        };

        self.clearSelectedFile = function() {
            self.selectedFile(null);
            self.uploadedFiles([]);
            self.fileError("");
            self.resetFilePickerSelection();
        };

        self.onFileSelect = function(event) {
            const files = event && event.detail ? event.detail.files : null,
                selectedFiles = files && files.length ? Array.prototype.slice.call(files) : [],
                nextFiles = self.uploadedFiles().concat(selectedFiles.map(toUploadEntry));

            if (!selectedFiles.length) {
                return;
            }

            userInteractionStarted = true;

            if (!validateSelectedFileMetadata(selectedFiles, nextFiles)) {
                return;
            }

            self.uploadedFiles(nextFiles);
            self.selectedFile(self.uploadedFiles()[0].file);
            self.fileError("");
        };

        self.onInvalidFile = function() {
            userInteractionStarted = true;
            showMessage(self.nls.messages.invalidPemFile, "ERROR");
        };

        self.onEdit = function() {
            userInteractionStarted = true;
            self.clearSelectedFile();
            self.mode("edit");
        };

        self.onDeleteFile = function(fileEntry) {
            userInteractionStarted = true;
            self.uploadedFiles.remove(fileEntry);
            self.selectedFile(self.uploadedFiles().length ? self.uploadedFiles()[0].file : null);
            self.fileError("");

            if (!self.uploadedFiles().length) {
                self.resetFilePickerSelection();
            }
        };

        self.onSubmit = function() {
            if (self.isSubmitting()) {
                return;
            }

            if (self.isSubmitDisabled()) {
                showMessage(self.nls.messages.noFileSelected, "ERROR");

                return;
            }

            if (hasOversizedFile(self.uploadedFiles())) {
                showMessage(self.nls.messages.pemFileTooLarge, "ERROR");

                return;
            }

            userInteractionStarted = true;
            submittedFiles = [];
            self.isSubmitting(true);

            readSelectedFiles()
                .then(validateReadCertificates)
                .then(submitReadCertificates)
                .then(handleSubmitResponse)
                .catch(handleSubmitError)
                .finally(function() {
                    self.isSubmitting(false);
                });
        };

        self.onCancel = function() {
            if (self.currentCertificateFiles().length && self.isEditMode()) {
                self.clearSelectedFile();
                self.mode("read");

                return;
            }

            rootParams.dashboard.switchModule(true);
        };

        self.onBack = function() {
            rootParams.dashboard.switchModule(true);
        };

        self.goToDashboard = function() {
            rootParams.dashboard.switchModule(null);
        };

        self.onDownloadCertificate = function(fileEntry, event) {
            if (event && event.preventDefault) {
                event.preventDefault();
            }

            const certificateFile = normalizeCertificateEntry(fileEntry);

            if (certificateFile.pemContent) {
                downloadLocalPem(certificateFile);

                return true;
            }

            if (!certificateFile.id) {
                return true;
            }

            CertificateModel.download(certificateFile.id);

            return true;
        };

        self.onSaveAsHtml = function() {
            if (typeof window !== "undefined" && window.print) {
                window.print();
            }
        };

        if (self.approvalMode()) {
            populateFromApprovalSnapshot(componentParams);
            configureApprovalConfirmScreen(componentParams);

            return;
        }

        loadExistingCertificates();
    };
});
