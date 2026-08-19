import { test, before, after, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} from '@firebase/rules-unit-testing';

let testEnv;

before(async () => {
  const rules = readFileSync('firestore.rules', 'utf8');
  testEnv = await initializeTestEnvironment({
    projectId: 'demo-pharmacy-project',
    firestore: {
      rules: rules,
      host: '127.0.0.1',
      port: 8088,
    },
  });
});

beforeEach(async () => {
  await testEnv.clearFirestore();

  // Seed baseline data using admin context (rules disabled)
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();

    // Registered Pharmacists
    await db.doc('registered_pharmacists/pharm_a').set({
      uid: 'pharm_a',
      fullName: 'Pharmacist A',
      branchId: 'BRANCH_A',
      role: 'Pharmacist',
      isSystemAdmin: false,
      isApproved: true,
      isSuspended: false,
    });

    await db.doc('registered_pharmacists/pharm_b').set({
      uid: 'pharm_b',
      fullName: 'Pharmacist B',
      branchId: 'BRANCH_B',
      role: 'Pharmacist',
      isSystemAdmin: false,
      isApproved: true,
      isSuspended: false,
    });

    await db.doc('registered_pharmacists/manager_a').set({
      uid: 'manager_a',
      fullName: 'Manager A',
      branchId: 'BRANCH_A',
      role: 'Branch Manager',
      isSystemAdmin: false,
      isApproved: true,
      isSuspended: false,
    });

    await db.doc('registered_pharmacists/admin_user').set({
      uid: 'admin_user',
      fullName: 'System Admin',
      branchId: 'BRANCH_A',
      role: 'Administrator',
      isSystemAdmin: true,
      isApproved: true,
      isSuspended: false,
    });

    await db.doc('registered_pharmacists/unapproved_user').set({
      uid: 'unapproved_user',
      fullName: 'Unapproved Staff',
      branchId: 'BRANCH_A',
      role: 'Pharmacist',
      isSystemAdmin: false,
      isApproved: false,
      isSuspended: false,
    });

    // Branch Inventory
    await db.doc('branch_inventory/BRANCH_A_inv1').set({
      id: 'inv1',
      branchId: 'BRANCH_A',
      name: 'Paracetamol 500mg',
      stockQuantity: 100,
    });

    await db.doc('branch_inventory/BRANCH_B_inv1').set({
      id: 'inv1',
      branchId: 'BRANCH_B',
      name: 'Amoxicillin 500mg',
      stockQuantity: 50,
    });

    // Branch Customers
    await db.doc('branch_customers/BRANCH_B_cust1').set({
      id: 'cust1',
      branchId: 'BRANCH_B',
      fullName: 'Customer B',
    });

    // Branch Customer Medications
    await db.doc('branch_customer_medications/BRANCH_B_med1').set({
      id: 'med1',
      branchId: 'BRANCH_B',
      medicationName: 'Aspirin',
    });

    // Branch Interventions
    await db.doc('branch_interventions/BRANCH_B_int1').set({
      id: 'int1',
      branchId: 'BRANCH_B',
      details: 'Intervention details B',
    });

    // Branch Operation Tasks
    await db.doc('branch_operation_tasks/BRANCH_B_task1').set({
      id: 'task1',
      branchId: 'BRANCH_B',
      title: 'Task B',
    });

    // Branch Receipts
    await db.doc('branch_receipts/BRANCH_B_rcpt1').set({
      id: 'rcpt1',
      branchId: 'BRANCH_B',
      receiptNumber: 'REC-001',
    });

    // Branch Outbound Logs
    await db.doc('branch_outbound_logs/BRANCH_B_log1').set({
      id: 'log1',
      branchId: 'BRANCH_B',
      message: 'SMS B',
    });

    // Medication Sales
    await db.doc('medication_sales/sale1_a').set({
      clientTransactionId: 'sale1_a',
      branchId: 'BRANCH_A',
      productName: 'Paracetamol 500mg',
      quantitySold: 2,
      totalAmount: 100.0,
      cashierUid: 'pharm_a',
    });

    await db.doc('medication_sales/sale1_b').set({
      clientTransactionId: 'sale1_b',
      branchId: 'BRANCH_B',
      productName: 'Amoxicillin 500mg',
      quantitySold: 1,
      totalAmount: 200.0,
      cashierUid: 'pharm_b',
    });

    // Device Configs
    await db.doc('device_configs/DEV_A1').set({
      deviceId: 'DEV_A1',
      branchId: 'BRANCH_A',
      ownerUid: 'pharm_a',
      status: 'Active',
    });

    await db.doc('device_configs/DEV_B1').set({
      deviceId: 'DEV_B1',
      branchId: 'BRANCH_B',
      ownerUid: 'pharm_b',
      status: 'Active',
    });

    // Expiry Rescue Listings
    await db.doc('expiry_rescue_listings/rescue_b1').set({
      listingId: 'rescue_b1',
      branchId: 'BRANCH_B',
      ownerUid: 'pharm_b',
      productName: 'Vitamin C',
      status: 'Available',
    });
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

// Helper contexts
function getPharmAContext() {
  return testEnv.authenticatedContext('pharm_a', {
    branchId: 'BRANCH_A',
    role: 'Pharmacist',
    isSystemAdmin: false,
    isApproved: true,
  });
}

function getPharmBContext() {
  return testEnv.authenticatedContext('pharm_b', {
    branchId: 'BRANCH_B',
    role: 'Pharmacist',
    isSystemAdmin: false,
    isApproved: true,
  });
}

function getManagerAContext() {
  return testEnv.authenticatedContext('manager_a', {
    branchId: 'BRANCH_A',
    role: 'Branch Manager',
    isSystemAdmin: false,
    isApproved: true,
  });
}

function getAdminContext() {
  return testEnv.authenticatedContext('admin_user', {
    branchId: 'BRANCH_A',
    role: 'Administrator',
    isSystemAdmin: true,
    isApproved: true,
  });
}

function getUnapprovedContext() {
  return testEnv.authenticatedContext('unapproved_user', {
    branchId: 'BRANCH_A',
    role: 'Pharmacist',
    isSystemAdmin: false,
    isApproved: false,
  });
}

function getUnauthContext() {
  return testEnv.unauthenticatedContext();
}

// ============================================================
// MANDATORY LIVE SECURITY TESTS
// ============================================================

test('1. CrossBranchInventoryReadDenied: Branch A pharmacist attempts to read Branch B inventory', async () => {
  const db = getPharmAContext().firestore();
  await assertFails(db.doc('branch_inventory/BRANCH_B_inv1').get());
});

test('2. CrossBranchInventoryWriteDenied: Branch A pharmacist attempts to create/update inventory belonging to Branch B', async () => {
  const db = getPharmAContext().firestore();
  await assertFails(
    db.doc('branch_inventory/BRANCH_B_inv2').set({
      id: 'inv2',
      branchId: 'BRANCH_B',
      name: 'Forged Stock',
      stockQuantity: 999,
    })
  );
});

test('3. CrossBranchCustomerReadDenied: Branch A pharmacist attempts to read Branch B customer data', async () => {
  const db = getPharmAContext().firestore();
  await assertFails(db.doc('branch_customers/BRANCH_B_cust1').get());
});

test('4. CrossBranchInterventionWriteDenied: Branch A pharmacist attempts to modify Branch B intervention', async () => {
  const db = getPharmAContext().firestore();
  await assertFails(
    db.doc('branch_interventions/BRANCH_B_int1').update({
      details: 'Unauthorized modification',
    })
  );
});

test('5. CrossBranchReceiptAccessDenied: Branch A pharmacist attempts to read/write Branch B receipt', async () => {
  const db = getPharmAContext().firestore();
  await assertFails(db.doc('branch_receipts/BRANCH_B_rcpt1').get());
  await assertFails(
    db.doc('branch_receipts/BRANCH_B_rcpt1').update({
      receiptNumber: 'HAX-000',
    })
  );
});

test('6. SelfAdminEscalationDenied: Authenticated pharmacist attempts isSystemAdmin: false -> true', async () => {
  const db = getPharmAContext().firestore();
  await assertFails(
    db.doc('registered_pharmacists/pharm_a').update({
      isSystemAdmin: true,
    })
  );
});

test('7. SelfRoleEscalationDenied: Pharmacist attempts role: "Pharmacist" -> "Branch Manager"', async () => {
  const db = getPharmAContext().firestore();
  await assertFails(
    db.doc('registered_pharmacists/pharm_a').update({
      role: 'Branch Manager',
    })
  );
});

test('8. SelfApprovalDenied: Unapproved pharmacist attempts isApproved: false -> true', async () => {
  const db = getUnapprovedContext().firestore();
  await assertFails(
    db.doc('registered_pharmacists/unapproved_user').update({
      isApproved: true,
    })
  );
});

test('9. CrossBranchSaleCreationDenied: Branch A pharmacist attempts to create medication_sales with branchId = BranchB', async () => {
  const db = getPharmAContext().firestore();
  await assertFails(
    db.doc('medication_sales/forged_sale').set({
      clientTransactionId: 'forged_sale',
      branchId: 'BRANCH_B',
      productName: 'Illegal Sale',
      quantitySold: 1,
      totalAmount: 50.0,
    })
  );
});

test('10. MedicationSaleUpdateDenied: Non-admin attempts to update an existing medication_sales document', async () => {
  const db = getPharmAContext().firestore();
  await assertFails(
    db.doc('medication_sales/sale1_a').update({
      totalAmount: 0.0,
    })
  );
});

test('11. MedicationSaleAdminAccessAllowed: System administrator updates an existing medication_sales document', async () => {
  const db = getAdminContext().firestore();
  await assertSucceeds(
    db.doc('medication_sales/sale1_a').update({
      notes: 'Admin adjusted audit record',
    })
  );
});

test('12. DeviceCrossBranchTamperingDenied: Branch A pharmacist attempts to modify a device belonging to Branch B', async () => {
  const db = getPharmAContext().firestore();
  await assertFails(
    db.doc('device_configs/DEV_B1').update({
      status: 'Disabled',
    })
  );
});

test('13. DeviceOwnerAccessAllowed: The authenticated owner updates their own device configuration', async () => {
  const db = getPharmAContext().firestore();
  await assertSucceeds(
    db.doc('device_configs/DEV_A1').update({
      status: 'Active',
      lastHeartbeat: 1700000000000,
    })
  );
});

test('14. DeviceOwnerSpoofDenied: User A attempts to change ownerUid of their device to User B', async () => {
  const db = getPharmAContext().firestore();
  await assertFails(
    db.doc('device_configs/DEV_A1').update({
      ownerUid: 'pharm_b',
    })
  );
});

test('15. ExpiryListingUnauthorizedModificationDenied: Pharmacist who is neither owner nor branch manager attempts to modify another branch listing', async () => {
  const db = getPharmAContext().firestore();
  await assertFails(
    db.doc('expiry_rescue_listings/rescue_b1').update({
      productName: 'Tampered Product',
    })
  );
});

test('16. ExpiryListingValidClaimAllowed: Legitimate claimant performs permitted Available -> Claimed transition without changing branchId or ownerUid', async () => {
  const db = getPharmAContext().firestore();
  await assertSucceeds(
    db.doc('expiry_rescue_listings/rescue_b1').update({
      status: 'Claimed',
    })
  );
});

test('17. Wildcard/UndefinedCollectionDenied: Attempt to access an undefined top-level collection/path', async () => {
  const db = getPharmAContext().firestore();
  await assertFails(db.doc('undefined_secret_collection/doc1').get());
  await assertFails(
    db.doc('undefined_secret_collection/doc1').set({ secret: true })
  );
});

test('18. UnauthenticatedAccessDenied: Unauthenticated client attempts to access protected operational data', async () => {
  const db = getUnauthContext().firestore();
  await assertFails(db.doc('branch_inventory/BRANCH_A_inv1').get());
  await assertFails(db.doc('medication_sales/sale1_a').get());
  await assertFails(db.doc('registered_pharmacists/pharm_a').get());
});

test('19. QueryTest: Scoped vs Unscoped medication_sales query authorization', async () => {
  const dbA = getPharmAContext().firestore();

  // Scoped query for own branch (BRANCH_A) MUST succeed
  const scopedQueryA = dbA
    .collection('medication_sales')
    .where('branchId', '==', 'BRANCH_A');
  await assertSucceeds(scopedQueryA.get());

  // Query for another branch (BRANCH_B) MUST be rejected
  const crossQuery = dbA
    .collection('medication_sales')
    .where('branchId', '==', 'BRANCH_B');
  await assertFails(crossQuery.get());

  // Unscoped query MUST be rejected to prevent silent data leakage
  const unscopedQuery = dbA.collection('medication_sales');
  await assertFails(unscopedQuery.get());
});
