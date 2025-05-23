// SPDX-License-Identifier: Apache-2.0

package com.hedera.services.evm.contracts.operations;

import com.hedera.node.app.service.evm.contracts.operations.HederaExceptionalHaltReason;
import java.util.function.BiPredicate;
import org.hiero.mirror.web3.evm.store.contract.HederaEvmStackedWorldStateUpdater;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.Words;
import org.hyperledger.besu.evm.operation.SelfDestructOperation;

/**
 * Hedera adapted version of the {@link SelfDestructOperation}.
 *
 * <p>Performs an existence check on the beneficiary {@link Address} Halts the execution of the EVM
 * transaction with {@link HederaExceptionalHaltReason#INVALID_SOLIDITY_ADDRESS} if the account does not exist, or it is
 * deleted.
 *
 * <p>Halts the execution of the EVM transaction with {@link
 * HederaExceptionalHaltReason#SELF_DESTRUCT_TO_SELF} if the beneficiary address is the same as the address being
 * destructed This class is a copy of HederaSelfDestructOperation from hedera-services mono
 */
public class HederaSelfDestructOperation extends HederaSelfDestructOperationBase {

    private final BiPredicate<Address, MessageFrame> addressValidator;

    public HederaSelfDestructOperation(
            final GasCalculator gasCalculator, final BiPredicate<Address, MessageFrame> addressValidator) {
        super(gasCalculator, false);
        this.addressValidator = addressValidator;
    }

    @Override
    public OperationResult execute(final MessageFrame frame, final EVM evm) {
        final var updater = (HederaEvmStackedWorldStateUpdater) frame.getWorldUpdater();
        final var beneficiaryAddress = Words.toAddress(frame.getStackItem(0));
        final var toBeDeleted = frame.getRecipientAddress();

        if (!addressValidator.test(beneficiaryAddress, frame)) {
            return reversionWith(null, HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS);
        }
        final var beneficiary = updater.get(beneficiaryAddress);

        final var exceptionalHaltReason = reasonToHalt(toBeDeleted, beneficiaryAddress, updater);
        if (exceptionalHaltReason != null) {
            return reversionWith(beneficiary, exceptionalHaltReason);
        }

        return super.execute(frame, evm);
    }
}
