-- Nom facultatif imprimé sous la fonction (ex. « Le Chef de Service » / « Dr Beyti Sylla »)
ALTER TABLE signataire
    ADD COLUMN nom VARCHAR(100) NULL AFTER fonction;
