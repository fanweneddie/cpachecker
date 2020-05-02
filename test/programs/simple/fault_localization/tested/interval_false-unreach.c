extern int __VERIFIER_nondet_int();

struct interval {
	int left;
	int right;
};


int compare(struct interval *i1, struct interval *i2){
	if (i1->left > i2->right) {
		return 1;
	}
	if (i1->right < i2->left) {
		return -1;
	}
	return 0;
}

int main(){

	struct interval i1;
	struct interval i2;
	
	i1.left = 5;
	i1.right = 4;
	i2.left = 1;
	i2.right = 3;

	if(compare(&i1, &i2) == 0){
		goto ERROR;
	}
	

EXIT: return 0;
ERROR: return 1;
}